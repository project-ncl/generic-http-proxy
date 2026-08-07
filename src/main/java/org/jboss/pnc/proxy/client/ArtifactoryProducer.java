/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.client;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.pnc.proxy.config.ArtifactoryConfig;
import org.jboss.pnc.proxy.config.ProxyConfiguration;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI producer for Artifactory client instances.
 * Creates a singleton Artifactory client configured with token-based authentication.
 */
@ApplicationScoped
public class ArtifactoryProducer {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactoryProducer.class);

    @Inject
    ArtifactoryConfig config;

    Artifactory artifactoryAdminClient;

    @Inject
    ProxyConfiguration proxyConfiguration;

    /**
     * Produces an application-scoped Artifactory client instance.
     * Uses token-based authentication from configuration.
     * 
     * @return configured Artifactory client
     */
    @Produces
    @Admin
    @ApplicationScoped
    public Artifactory createArtifactoryAdminClient() {
        logger.info("Creating Artifactory client for URL: {}", config.url());

        artifactoryAdminClient = ArtifactoryClientBuilder.create()
                .setUrl(config.url())
                .setAccessToken(config.accessToken())
                .setConnectionTimeout(config.connectionTimeout())
                .setSocketTimeout(config.socketTimeout())
                .build();

        return artifactoryAdminClient;
    }

    public Artifactory fromBuildToken(String user, String buildToken) {
        logger.info("Creating Artifactory client for Build user: {}", user);

        return switch (proxyConfiguration.getTrackingType()) {
            case ALWAYS -> ArtifactoryClientBuilder.create()
                    .setUrl(config.url())
                    .setAccessToken(buildToken)
                    .setConnectionTimeout(config.connectionTimeout())
                    .setSocketTimeout(config.socketTimeout())
                    .build();
            case STATIC -> ArtifactoryClientBuilder.create()
                    .setUrl(config.url())
                    .setAccessToken(
                            config.staticTrackingPassword()
                                    .orElseThrow(() -> new IllegalStateException("Static password not present.")))
                    .setConnectionTimeout(config.connectionTimeout())
                    .setSocketTimeout(config.socketTimeout())
                    .build();
            case SUFFIX -> throw new UnsupportedOperationException("Suffix tracking not supported.");
            case NEVER -> null;
        };
    }

    @PreDestroy
    public void destroy() {
        artifactoryAdminClient.close();
        logger.info("Closing Artifactory client for URL.");
    }
}
