/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.handler;

import static java.lang.Integer.parseInt;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.jboss.pnc.proxy.util.ApplicationHeader.proxy_authenticate;
import static org.jboss.pnc.proxy.util.ApplicationStatus.PROXY_AUTHENTICATION_REQUIRED;
import static org.jboss.pnc.proxy.util.HttpProxyConstants.ALLOW_HEADER_VALUE;
import static org.jboss.pnc.proxy.util.HttpProxyConstants.CONNECT_METHOD;
import static org.jboss.pnc.proxy.util.HttpProxyConstants.GET_METHOD;
import static org.jboss.pnc.proxy.util.HttpProxyConstants.HEAD_METHOD;
import static org.jboss.pnc.proxy.util.HttpProxyConstants.OPTIONS_METHOD;
import static org.jboss.pnc.proxy.util.HttpProxyConstants.PROXY_AUTHENTICATE_FORMAT;
import static org.jboss.pnc.proxy.util.HttpProxyConstants.TRACKING_ID;
import static org.jboss.pnc.proxy.util.UserPass.parse;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.channels.SocketChannel;

import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.proxy.client.repository.ArtifactoryRepositoryManager;
import org.jboss.pnc.proxy.config.ProxyConfiguration;
import org.jboss.pnc.proxy.model.RemoteRepository;
import org.jboss.pnc.proxy.model.TrackingType;
import org.jboss.pnc.proxy.util.ApplicationHeader;
import org.jboss.pnc.proxy.util.ApplicationStatus;
import org.jboss.pnc.proxy.util.ArtifactoryProxyResponseHelper;
import org.jboss.pnc.proxy.util.HttpConduitWrapper;
import org.jboss.pnc.proxy.util.HttpWrapper;
import org.jboss.pnc.proxy.util.OtelAdapter;
import org.jboss.pnc.proxy.util.ProxyMeter;
import org.jboss.pnc.proxy.util.UserPass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.ChannelListener;
import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

import io.opentelemetry.api.trace.Span;

public final class ProxyResponseWriter
        implements ChannelListener<ConduitStreamSinkChannel> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Throwable error;
    private HttpRequest httpRequest;
    private final ProxyConfiguration config;

    private final ConduitStreamSourceChannel sourceChannel;
    private final SocketAddress peerAddress;

    private final ArtifactoryRepositoryManager repositoryManager;

    private ProxySSLTunnel sslTunnel;
    private boolean directed = false;

    private ProxyRequestReader proxyRequestReader;
    private final ManagedExecutor tunnelAndMITMExecutor;

    private final OtelAdapter otel;

    private final long startNanos;

    public ProxyResponseWriter(
            final ProxyConfiguration config,
            final StreamConnection accepted,
            final ArtifactoryRepositoryManager repositoryManager,
            final ManagedExecutor executor,
            final long start,
            final OtelAdapter otel) {
        this.config = config;
        this.peerAddress = accepted.getPeerAddress();
        this.sourceChannel = accepted.getSourceChannel();
        this.repositoryManager = repositoryManager;
        this.tunnelAndMITMExecutor = executor;
        this.startNanos = start;
        this.otel = otel;
    }

    public ProxyRequestReader getProxyRequestReader() {
        return proxyRequestReader;
    }

    public void setProxyRequestReader(ProxyRequestReader proxyRequestReader) {
        this.proxyRequestReader = proxyRequestReader;
    }

    @Override
    public void handleEvent(final ConduitStreamSinkChannel channel) {
        doHandleEvent(channel);
    }

    private void doHandleEvent(final ConduitStreamSinkChannel sinkChannel) {
        if (directed) {
            return;
        }

        ProxyMeter meter = new ProxyMeter(
                httpRequest.getRequestLine().getMethod(),
                httpRequest.getRequestLine().toString(),
                startNanos,
                peerAddress,
                otel);

        HttpConduitWrapper http = new HttpConduitWrapper(sinkChannel, httpRequest);
        if (httpRequest == null) {
            if (error != null) {
                logger.debug("Handling error from request reader: {}", error.getMessage(), error);
                handleError(error, http);
            } else {
                handleBadRequest(http);
            }
            closeQuietly(sinkChannel);
            closeQuietly(sourceChannel);
            return;
        }

        final String oldThreadName = Thread.currentThread().getName();
        // FIXME make this ThreadLocal
        Thread.currentThread().setName("PROXY-" + httpRequest.getRequestLine().toString());
        sinkChannel.getCloseSetter().set((c) -> {
            logger.trace("Sink channel closing...");
            // FIXME I very much doubt that this is actually guaranteeing that the calling thread of this lambda is the
            //  same as the one that was named `oldThreadName`
            Thread.currentThread().setName(oldThreadName); // restore original thread name
            if (sslTunnel != null) {
                logger.trace("Close ssl tunnel");
                sslTunnel.close();
            }
            try {
                sourceChannel.close();
            } catch (IOException e) {
                logger.warn("Close source channel failed", e);
            }
        });

        logger.debug("\n\n\n>>>>>>> Handle write\n\n\n");
        if (error == null) {

            ArtifactoryProxyResponseHelper proxyResponseHelper = new ArtifactoryProxyResponseHelper(
                    httpRequest,
                    repositoryManager,
                    otel);

            try {

                final UserPass proxyUserPass = parse(ApplicationHeader.proxy_authorization, httpRequest, null);
                logger.info("Using proxy authentication: {}", proxyUserPass);

                logger.debug(
                        "Proxy UserPass: {}\nConfig secured? {}\nConfig tracking type: {}",
                        proxyUserPass,
                        config.isSecured(),
                        config.getTrackingType());
                if (proxyUserPass == null && (config.isSecured() || TrackingType.ALWAYS == config.getTrackingType())) {

                    String realmInfo = String.format(PROXY_AUTHENTICATE_FORMAT, config.getProxyRealm());

                    logger.info(
                            "Not authenticated to proxy. Sending response: {} / {}: {}",
                            PROXY_AUTHENTICATION_REQUIRED,
                            proxy_authenticate,
                            realmInfo);

                    http.writeStatus(PROXY_AUTHENTICATION_REQUIRED);
                    http.writeHeader(proxy_authenticate, String.format("%s\n", realmInfo));
                } else {
                    RequestLine requestLine = httpRequest.getRequestLine();
                    String method = requestLine.getMethod().toUpperCase();
                    boolean authenticated = true;

                    String trackingId = null;
                    if (proxyUserPass != null) {
                        trackingId = repositoryManager.resolveTrackingId(proxyUserPass);
                        if (trackingId != null) {
                            if (otel.enabled()) {
                                Span.current().setAttribute(TRACKING_ID, trackingId);
                            }
                        }

                        /*
                         * String authCacheKey = generateAuthCacheKey( proxyUserPass );
                         * Boolean isAuthToken = false;//proxyAuthCache.get( authCacheKey );
                         * if ( Boolean.TRUE.equals( isAuthToken ) )
                         * {
                         * authenticated = true;
                         * logger.debug( "Found auth key in cache" );
                         * }
                         * else
                         * {
                         * logger.debug(
                         * "Passing BASIC authentication credentials to Keycloak bearer-token translation authenticator"
                         * );
                         * authenticated = proxyAuthenticator.authenticate( proxyUserPass, http );
                         *//*
                            * if ( authenticated )
                            * {
                            * proxyAuthCache.put( authCacheKey, Boolean.TRUE, config.getAuthCacheExpirationHours(),
                            * TimeUnit.HOURS );
                            * }
                            *//*
                               * }
                               * logger.debug( "Authentication done, result: {}", authenticated );
                               */

                    }

                    if (authenticated) {
                        switch (method) {
                            case GET_METHOD:
                            case HEAD_METHOD: {
                                final URL url = new URL(requestLine.getUri());
                                logger.debug("Get repository, trackingId: {}, url: {}", trackingId, url);
                                RemoteRepository repo = proxyResponseHelper
                                        .getRepository(trackingId, url, proxyUserPass);
                                // 'url.getFile()' gets the file name of this URL. The returned file portion will be the
                                // same as getPath(), plus the concatenation of the value of getQuery(), if any.
                                proxyResponseHelper.transfer(
                                        http,
                                        repo,
                                        url.getFile(),
                                        GET_METHOD.equals(method),
                                        proxyUserPass,
                                        meter);
                                break;
                            }
                            case OPTIONS_METHOD: {
                                http.writeStatus(ApplicationStatus.OK);
                                http.writeHeader(ApplicationHeader.allow, ALLOW_HEADER_VALUE);
                                break;
                            }
                            case CONNECT_METHOD: {
                                if (!config.isMITMEnabled()) {
                                    logger.debug("CONNECT method not supported unless MITM-proxying is enabled.");
                                    http.writeStatus(ApplicationStatus.BAD_REQUEST);
                                    break;
                                }

                                String uri = requestLine.getUri(); // e.g, github.com:443
                                logger.debug("Get CONNECT request, uri: {}", uri);

                                String[] toks = uri.split(":");
                                String host = toks[0];
                                int port = parseInt(toks[1]);

                                directed = true;

                                // After this, the proxy simply opens a plain socket to the target server and relays
                                // everything between the initial client and the target server (including the TLS handshake).

                                SocketChannel socketChannel;

                                ProxyMITMSSLServer svr = new ProxyMITMSSLServer(
                                        host,
                                        port,
                                        trackingId,
                                        proxyUserPass,
                                        proxyResponseHelper,
                                        config,
                                        meter,
                                        http);
                                tunnelAndMITMExecutor.submit(svr);
                                socketChannel = svr.getSocketChannel();

                                if (socketChannel == null) {
                                    logger.debug("Failed to get MITM socket channel");
                                    http.writeStatus(ApplicationStatus.SERVER_ERROR);
                                    svr.stop();
                                    break;
                                }

                                sslTunnel = new ProxySSLTunnel(sinkChannel, socketChannel, config);
                                tunnelAndMITMExecutor.submit(sslTunnel);
                                proxyRequestReader.setProxySSLTunnel(sslTunnel); // client input will be directed to target socket
                                svr.setProxySSLTunnel(sslTunnel);

                                // When all is ready, send the 200 to client. Client send the SSL handshake to reader,
                                // reader direct it to tunnel to MITM. MITM finish the handshake and read the request data,
                                // retrieve remote content and send back to tunnel to client.
                                http.writeStatus(ApplicationStatus.OK);
                                http.writeHeader("Status", "200 OK\r\n");

                                break;
                            }
                            default: {
                                http.writeStatus(ApplicationStatus.METHOD_NOT_ALLOWED);
                            }
                        }
                    }
                }
                logger.debug("Response complete.");
            } catch (final Throwable e) {
                error = e;
            }
        }

        if (error != null) {
            handleError(error, http);
        }

        if (directed) {
            // do not close sink channel
        } else {
            closeQuietly(http);
            closeQuietly(sinkChannel);
            closeQuietly(sourceChannel);
        }
    }

    private void handleBadRequest(HttpConduitWrapper http) {
        logger.warn("Invalid state (no error or request) from request reader. Sending 400.");
        try {
            http.writeStatus(ApplicationStatus.BAD_REQUEST);
        } catch (IOException e) {
            logger.warn("Failed to write BAD_REQUEST", e);
        }
    }

    private String generateAuthCacheKey(UserPass proxyUserPass) {
        return sha256Hex(proxyUserPass.getUser() + ":" + proxyUserPass.getPassword());
    }

    private void handleError(final Throwable error, final HttpWrapper http) {
        logger.error("HTTProx request failed: " + error.getMessage(), error);
        try {
            if (http.isOpen()) {
                http.writeStatus(ApplicationStatus.SERVER_ERROR);
                http.writeError(error);
                logger.debug("Response error complete.");
            }
        } catch (final IOException e) {
            logger.warn("Failed to write error: " + error.getMessage(), error);
        }
    }

    public void setError(final Throwable error) {
        this.error = error;
    }

    public void setHttpRequest(final HttpRequest request) {
        this.httpRequest = request;
    }
}
