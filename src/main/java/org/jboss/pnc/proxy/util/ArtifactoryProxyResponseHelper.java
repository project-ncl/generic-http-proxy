/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.util;

import static org.jboss.pnc.proxy.util.HttpProxyConstants.TRACKING_ID;
import static org.jboss.pnc.proxy.util.MetricsConstants.CONTENT_ENTRY_POINT;
import static org.jboss.pnc.proxy.util.MetricsConstants.METADATA_CONTENT;
import static org.jboss.pnc.proxy.util.MetricsConstants.PACKAGE_TYPE;
import static org.jboss.pnc.proxy.util.MetricsConstants.PATH;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.jboss.pnc.proxy.client.repository.ArtifactoryRepositoryManager;
import org.jboss.pnc.proxy.model.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.Span;
import io.smallrye.mutiny.Uni;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Artifactory-based proxy response helper.
 */
public class ArtifactoryProxyResponseHelper {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HttpRequest httpRequest;
    private final ArtifactoryRepositoryManager repositoryManager;
    private final OtelAdapter otel;

    private volatile boolean transferred;

    public ArtifactoryProxyResponseHelper(
            HttpRequest httpRequest,
            ArtifactoryRepositoryManager repositoryManager,
            OtelAdapter otel) {
        this.httpRequest = httpRequest;
        this.repositoryManager = repositoryManager;
        this.otel = otel;
    }

    /**
     * Get or create remote repository for URL.
     * Validates credentials first, then uses single Manager entrypoint.
     */
    public RemoteRepository getRepository(String trackingId, final URL url, UserPass proxyUserPass)
            throws GenericProxyException {
        // Validate credentials BEFORE creating repository - fail fast
        repositoryManager.validateCredentials(proxyUserPass);

        RemoteRepository repo = repositoryManager.getOrCreateRepository(url, httpRequest);

        if (repo != null) {
            logger.info("Got repository {} for url {}", repo.getKey(), url);
        }

        if (otel.enabled()) {
            Span span = Span.current();
            span.setAttribute("proxy.target.url", String.valueOf(url));
            if (trackingId != null) {
                span.setAttribute(TRACKING_ID, trackingId);
            }
            if (repo != null) {
                span.setAttribute(PACKAGE_TYPE, "generic");
                span.setAttribute(CONTENT_ENTRY_POINT, repo.getKey());
            }
        }

        return repo;
    }

    /**
     * Transfer content from Artifactory to client.
     * Credentials passed to Artifactory for tracking via Manager.
     */
    public void transfer(
            final HttpConduitWrapper http,
            final RemoteRepository repo,
            final String path,
            final boolean writeBody,
            final UserPass proxyUserPass,
            final ProxyMeter meter)
            throws IOException, GenericProxyException {

        if (otel.enabled()) {
            Span.current().setAttribute(PATH, path);
            Span.current().setAttribute(METADATA_CONTENT, Boolean.FALSE);
        }

        doTransfer(http, repo, path, writeBody, proxyUserPass, meter);
    }

    private void doTransfer(
            final HttpConduitWrapper http,
            final RemoteRepository repo,
            final String path,
            final boolean writeBody,
            final UserPass proxyUserPass,
            final ProxyMeter meter)
            throws IOException, GenericProxyException {

        if (transferred) {
            logger.info("Transfer already done, repo: {}, path: {}", repo.getKey(), path);
            return;
        }

        if (!http.isOpen()) {
            throw new IOException("Sink channel already closed (or null)!");
        }

        String trackingId = repositoryManager.resolveTrackingId(proxyUserPass);
        boolean isTracking = repositoryManager.isTracking(proxyUserPass);

        if (trackingId != null && isTracking) {
            logger.info("TRACKING {} in {} (KEY: {})", path, repo.getKey(), trackingId);
        } else {
            logger.debug("NOT TRACKING: {} in {}", path, repo.getKey());
        }

        try {
            logger.debug("Get from Artifactory, repo: {}, path: {}", repo.getKey(), path);

            // Use Manager for content fetching - returns Uni<Response> with full HTTP response
            Uni<Response> responseUni = repositoryManager.fetch(repo, path, proxyUserPass);

            final CountDownLatch transferLatch = new CountDownLatch(1);

            responseUni.subscribe()
                    .with(
                            response -> {
                                ResponseBody responseBody = response.body();
                                try {
                                    if (response.code() == HttpStatus.SC_NOT_FOUND) {
                                        http.writeNotFoundTransfer(repo, path);
                                    } else if (response.isSuccessful()) {
                                        try (InputStream bodyInputStream = responseBody.byteStream()) {
                                            http.writeExistingTransfer(bodyInputStream, writeBody, response.headers());
                                        }
                                    } else {
                                        logger.warn(
                                                "Artifactory returned error: {} {}",
                                                response.code(),
                                                response.message());
                                        http.writeError(
                                                new IOException(
                                                        "Artifactory error: " + response.code() + " "
                                                                + response.message()));
                                    }
                                } catch (IOException e) {
                                    logger.error("write transfer error: {}", e.getMessage(), e);
                                } finally {
                                    if (responseBody != null) {
                                        responseBody.close();
                                    }
                                    response.close();
                                    transferred = true;
                                    transferLatch.countDown();
                                }
                            },
                            throwable -> {
                                try {
                                    http.writeError(throwable);
                                } catch (IOException e) {
                                    logger.error("write error: {}", e.getMessage(), e);
                                } finally {
                                    transferred = true;
                                    transferLatch.countDown();
                                }
                            });

            transferLatch.await(5, TimeUnit.MINUTES);

            if (meter != null) {
                meter.reportResponseSummary();
            }
        } catch (Exception exception) {
            logger.error("doTransfer error: {}", exception.getMessage(), exception);
            throw new GenericProxyException("Transfer failed", exception);
        }
    }
}