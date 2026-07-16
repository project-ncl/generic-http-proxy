/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.handler;

import static org.commonjava.indy.pkg.PackageTypeConstants.PKG_TYPE_GENERIC_HTTP;
import static org.jboss.pnc.proxy.util.MetricsConstants.*;

import java.io.IOException;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.commonjava.indy.model.core.io.IndyObjectMapper;
import org.jboss.pnc.proxy.client.content.ContentRetrievalService;
import org.jboss.pnc.proxy.client.repository.RepositoryService;
import org.jboss.pnc.proxy.config.ProxyConfiguration;
import org.jboss.pnc.proxy.keycloak.KeycloakProxyAuthenticator;
import org.jboss.pnc.proxy.util.CacheProducer;
import org.jboss.pnc.proxy.util.OtelAdapter;
import org.jboss.pnc.proxy.util.RepoCreator;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.ChannelListener;
import org.xnio.StreamConnection;
import org.xnio.channels.AcceptingChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

import io.opentelemetry.api.trace.Span;

@ApplicationScoped
public class ProxyAcceptHandler implements ChannelListener<AcceptingChannel<StreamConnection>> {

    public static final String HTTPROX_ORIGIN = "httprox";

    @Inject
    ProxyConfiguration config;

    @Inject
    @RestClient
    RepositoryService repositoryService;

    @Inject
    ContentRetrievalService contentRetrievalService;

    @Inject
    KeycloakProxyAuthenticator proxyAuthenticator;

    @Inject
    @Named("mitm-transfers")
    ManagedExecutor proxyExecutor;

    @Inject
    OtelAdapter otel;

    @Inject
    CacheProducer cacheProducer;

    IndyObjectMapper objectMapper;

    public ProxyAcceptHandler() {

    }

    @PostConstruct
    public void post() {
        objectMapper = new IndyObjectMapper(false);
    }

    @Override
    public void handleEvent(AcceptingChannel<StreamConnection> channel) {
        final Logger logger = LoggerFactory.getLogger(getClass());
        long start = System.nanoTime();
        if (otel.enabled()) {
            Span.current().setAttribute(ACCESS_CHANNEL, PKG_TYPE_GENERIC_HTTP);
            Span.current().setAttribute(PACKAGE_TYPE, PKG_TYPE_GENERIC_HTTP);
        }

        StreamConnection accepted;
        try {
            accepted = channel.accept();
        } catch (IOException e) {
            logger.error("Failed to accept httprox connection: " + e.getMessage(), e);
            accepted = null;
        }

        // to remove the return in the catch clause, which is bad form...
        if (accepted == null) {
            return;
        }

        if (otel.enabled()) {
            Span.current().setAttribute(REQUEST_PHASE, REQUEST_PHASE_START);
        }

        logger.info("accepted request from address: {}", accepted.getPeerAddress());

        final ConduitStreamSourceChannel source = accepted.getSourceChannel();
        final ConduitStreamSinkChannel sink = accepted.getSinkChannel();

        ProxyRepositoryCreator repoCreator = new RepoCreator(config);

        final ProxyResponseWriter writer = new ProxyResponseWriter(
                config,
                repoCreator,
                accepted,
                repositoryService,
                contentRetrievalService,
                proxyExecutor,
                proxyAuthenticator,
                objectMapper,
                cacheProducer,
                start,
                otel);

        logger.debug("Setting writer: {}", writer);
        sink.getWriteSetter().set(writer);

        final ProxyRequestReader reader = new ProxyRequestReader(writer, sink);
        writer.setProxyRequestReader(reader);

        logger.debug("Setting reader: {}", reader);
        source.getReadSetter().set(reader);
        source.resumeReads();
    }
}
