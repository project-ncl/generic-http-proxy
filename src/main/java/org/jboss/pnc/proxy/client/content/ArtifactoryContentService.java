/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.client.content;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.pnc.proxy.config.ArtifactoryConfig;
import org.jboss.pnc.proxy.config.ServiceProxyConfig;
import org.jboss.pnc.proxy.config.ServiceProxyConfig.ServiceConfig;
import org.jboss.pnc.proxy.util.OtelAdapter;
import org.jboss.pnc.proxy.util.WebClientAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import okhttp3.Response;

/**
 * Service for managing artifact content in Artifactory using WebClientAdapter pattern.
 */
@ApplicationScoped
public class ArtifactoryContentService {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactoryContentService.class);

    private final ArtifactoryConfig artifactoryConfig;

    private final WebClientAdapter webClient;

    private final AtomicLong timeout = new AtomicLong(TimeUnit.MINUTES.toMillis(5));

    public ArtifactoryContentService(
            ArtifactoryConfig artifactoryConfig,
            ServiceProxyConfig proxyConfiguration,
            OtelAdapter otel) {
        this.artifactoryConfig = artifactoryConfig;

        ServiceConfig artifactoryService = createArtifactoryServiceConfig(artifactoryConfig);
        this.webClient = new WebClientAdapter(artifactoryService, proxyConfiguration, timeout, otel);
    }

    /**
     * Create ServiceConfig for Artifactory from ArtifactoryConfig.
     */
    private static ServiceConfig createArtifactoryServiceConfig(ArtifactoryConfig rtConfig) {

        // Parse Artifactory URL to extract host, port, ssl
        String url = rtConfig.url();
        boolean ssl = url.startsWith("https://");
        String hostPort = url.replace("https://", "").replace("http://", "");

        // Extract host and port
        String host;
        int port = ssl ? 443 : 80;

        if (hostPort.contains(":")) {
            String[] parts = hostPort.split(":");
            host = parts[0];
            if (parts.length > 1) {
                try {
                    port = Integer.parseInt(parts[1].split("/")[0]);
                } catch (NumberFormatException e) {
                    logger.warn("Failed to parse port from URL: {}, using default: {}", url, port);
                }
            }
        } else {
            host = hostPort.split("/")[0];
        }

        ServiceConfig config = convertToConfig(port, host, ssl);

        logger.debug("Created Artifactory ServiceConfig: host={}, port={}, ssl={}", host, port, ssl);

        return config;
    }

    private static @NotNull ServiceConfig convertToConfig(int port, String host, boolean ssl) {
        return new ServiceConfig() {
            @Override
            public String host() {
                return host;
            }

            @Override
            public int port() {
                return port;
            }

            @Override
            public boolean ssl() {
                return ssl;
            }

            @Override
            public Optional<String> methods() {
                return Optional.empty();
            }

            @Override
            public String pathPattern() {
                return null;
            }
        };
    }

    /**
     * Perform GET request to Artifactory.
     * Passes incoming headers from client request to Artifactory.
     * Returns full Response with status codes, headers, and body.
     *
     * @param buildToken the build token for authentication (can be null for NEVER tracking)
     * @param user the user for tracking (can be null for NEVER tracking)
     * @param repoKey the repository key
     * @param path the artifact path within the repository
     * @param request the original client request (for header forwarding)
     * @return Uni with full Response object
     */
    public Uni<Response> doGet(String buildToken, String user, String repoKey, String path, HttpServerRequest request) {
        try {
            logger.debug("GET artifact from {}/{} with user: {}", repoKey, path, user);

            String artifactPath = buildArtifactPath(repoKey, path);

            WebClientAdapter.RequestAdapter adapter = webClient.get(artifactPath, request);
            addAuthHeaders(adapter, buildToken);

            return adapter.call().enqueue();
        } catch (Exception e) {
            logger.error("Error in GET request to {}/{}: {}", repoKey, path, e.getMessage(), e);
            return Uni.createFrom().failure(e);
        }
    }

    /**
     * Perform HEAD request to Artifactory.
     *
     * @param buildToken the build token for authentication
     * @param user the user for tracking
     * @param repoKey the repository key
     * @param path the artifact path
     * @param request the original client request
     * @return Uni with Response (check status: 200=exists, 404=not found)
     */
    public Uni<Response> doHead(
            String buildToken,
            String user,
            String repoKey,
            String path,
            HttpServerRequest request) {
        try {
            logger.debug("HEAD artifact {}/{} with user: {}", repoKey, path, user);

            String artifactPath = buildArtifactPath(repoKey, path);

            WebClientAdapter.RequestAdapter adapter = webClient.head(artifactPath, request);
            addAuthHeaders(adapter, buildToken);

            return adapter.call().enqueue();
        } catch (Exception e) {
            logger.error("Error in HEAD request to {}/{}: {}", repoKey, path, e.getMessage(), e);
            return Uni.createFrom().failure(e);
        }
    }

    /**
     * Perform POST request to Artifactory.
     * Request body is automatically cached to temp file by WebClientAdapter.
     *
     * @param buildToken the build token for authentication
     * @param user the user for tracking
     * @param repoKey the repository key
     * @param path the artifact path
     * @param body the request body as InputStream
     * @param request the original client request
     * @return Uni with Response object
     */
    public Uni<Response> doPost(
            String buildToken,
            String user,
            String repoKey,
            String path,
            InputStream body,
            HttpServerRequest request) {
        try {
            logger.debug("POST artifact to {}/{} with user: {}", repoKey, path, user);

            String artifactPath = buildArtifactPath(repoKey, path);

            WebClientAdapter.RequestAdapter adapter = webClient.post(artifactPath, body, request);
            addAuthHeaders(adapter, buildToken);

            return adapter.call().enqueue();
        } catch (Exception e) {
            logger.error("Error in POST request to {}/{}: {}", repoKey, path, e.getMessage(), e);
            return Uni.createFrom().failure(e);
        }
    }

    /**
     * Perform PUT request to Artifactory.
     * Request body is automatically cached to temp file by WebClientAdapter.
     *
     * @param buildToken the build token for authentication
     * @param user the user for tracking
     * @param repoKey the repository key
     * @param path the artifact path
     * @param body the request body as InputStream
     * @param request the original client request
     * @return Uni with Response object
     */
    public Uni<Response> doPut(
            String buildToken,
            String user,
            String repoKey,
            String path,
            InputStream body,
            HttpServerRequest request) {
        try {
            logger.debug("PUT artifact to {}/{} with user: {}", repoKey, path, user);

            String artifactPath = buildArtifactPath(repoKey, path);

            WebClientAdapter.RequestAdapter adapter = webClient.put(artifactPath, body, request);
            addAuthHeaders(adapter, buildToken);

            return adapter.call().enqueue();
        } catch (Exception e) {
            logger.error("Error in PUT request to {}/{}: {}", repoKey, path, e.getMessage(), e);
            return Uni.createFrom().failure(e);
        }
    }

    /**
     * Perform DELETE request to Artifactory.
     *
     * @param buildToken the build token for authentication
     * @param user the user for tracking
     * @param repoKey the repository key
     * @param path the artifact path
     * @return Uni with Response object
     */
    public Uni<Response> doDelete(String buildToken, String user, String repoKey, String path) {
        try {
            logger.debug("DELETE artifact {}/{} with user: {}", repoKey, path, user);

            String artifactPath = buildArtifactPath(repoKey, path);

            WebClientAdapter.RequestAdapter adapter = webClient.delete(artifactPath);
            addAuthHeaders(adapter, buildToken);

            return adapter.call().enqueue();
        } catch (Exception e) {
            logger.error("Error in DELETE request to {}/{}: {}", repoKey, path, e.getMessage(), e);
            return Uni.createFrom().failure(e);
        }
    }

    /**
     * Validate Build client credentials by attempting to access Artifactory.
     *
     * @param buildToken the build token to validate
     * @param user the user for the build client
     * @return true if credentials are valid, false otherwise
     */
    public boolean validateBuildCredentials(String buildToken, String user) {
        try {
            logger.debug("Validating build credentials for user: {}", user);

            // Test access by listing repositories (lightweight operation)
            String path = "/artifactory/api/v2/repositories/" + artifactoryConfig.projectKey() + "-mvn-ibm-builds";

            WebClientAdapter.RequestAdapter adapter = webClient.get(path);
            addAuthHeaders(adapter, buildToken);

            Response response = adapter.call()
                    .enqueue()
                    .await()
                    .atMost(java.time.Duration.ofSeconds(10));

            boolean valid = response.isSuccessful();
            logger.debug("Build credentials validation result: {} (status={})", valid, response.code());

            response.close();
            return valid;
        } catch (Exception e) {
            logger.warn("Build credential validation failed for user {}: {}", user, e.getMessage());
            return false;
        }
    }

    /**
     * Build the full artifact path for Artifactory: /artifactory/{repo-key}/{path}
     */
    private String buildArtifactPath(String repoKey, String path) {
        // Ensure path starts with /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "/artifactory/" + repoKey + path;
    }

    /**
     * Add authentication and tracking headers to request.
     *
     * @param adapter the RequestAdapter to add headers to
     * @param buildToken the build token (can be null)
     */
    private void addAuthHeaders(WebClientAdapter.RequestAdapter adapter, String buildToken) {
        // Add authentication header
        if (buildToken != null && !buildToken.isEmpty()) {
            adapter.addHeader("Authorization", "Bearer " + buildToken);
        } else {
            adapter.addHeader("Authorization", "Bearer " + artifactoryConfig.accessToken());
        }

    }
}