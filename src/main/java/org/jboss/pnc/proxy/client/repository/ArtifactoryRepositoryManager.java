/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.client.repository;

import java.net.URL;
import java.util.concurrent.locks.Lock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.http.HttpRequest;
import org.jboss.pnc.proxy.client.content.ArtifactoryContentService;
import org.jboss.pnc.proxy.config.ArtifactoryConfig;
import org.jboss.pnc.proxy.config.ProxyConfiguration;
import org.jboss.pnc.proxy.model.GenericRepositoryKey;
import org.jboss.pnc.proxy.model.RemoteRepository;
import org.jboss.pnc.proxy.model.TrackingType;
import org.jboss.pnc.proxy.util.ApplicationHeader;
import org.jboss.pnc.proxy.util.ApplicationStatus;
import org.jboss.pnc.proxy.util.CacheProducer;
import org.jboss.pnc.proxy.util.GenericProxyException;
import org.jboss.pnc.proxy.util.LockWrapper;
import org.jboss.pnc.proxy.util.UrlInfo;
import org.jboss.pnc.proxy.util.UserPass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.smallrye.mutiny.Uni;
import okhttp3.Response;

@ApplicationScoped
public class ArtifactoryRepositoryManager {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactoryRepositoryManager.class);
    private static final String REPOSITORY_CACHE = "artifact_store";

    @Inject
    ArtifactoryRepositoryService repositoryService;

    @Inject
    ArtifactoryContentService contentService;

    @Inject
    ArtifactoryConfig artifactoryConfig;

    @Inject
    CacheProducer cacheProducer;

    @Inject
    ProxyConfiguration proxyConfiguration;

    /**
     * Get or create repository for the given URL.
     * Uses Admin client for repository operations.
     * Thread-safe with per-repository locking and caching.
     * 
     * @param url the upstream URL
     * @param httpRequest the HTTP request (for upstream credentials)
     * @return RemoteRepository for the upstream host
     * @throws GenericProxyException if repository operations fail
     */
    public RemoteRepository getOrCreateRepository(URL url, HttpRequest httpRequest) throws GenericProxyException {
        String repositoryName = getRepositoryName(url);
        Lock lock = LockWrapper.getLockByKey(repositoryName);
        lock.lock();

        Cache<String, RemoteRepository> cache = cacheProducer.getCache(REPOSITORY_CACHE);
        try {
            // Check cache first
            RemoteRepository cached = cache.getIfPresent(repositoryName);
            if (cached != null) {
                logger.debug("Repository {} found in cache", repositoryName);
                return cached;
            }

            // Try to get existing repository from Artifactory (Admin client)
            RemoteRepository existing = repositoryService.getRepository(repositoryName);
            if (existing != null) {
                logger.debug("Repository {} found in Artifactory", repositoryName);
                cache.put(repositoryName, existing);
                return existing;
            }

            // Create new repository (Admin client)
            logger.info("Creating new repository {} for URL {}", repositoryName, url);
            RemoteRepository created = createRepository(url, httpRequest, repositoryName);

            cache.put(repositoryName, created);
            return created;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fetch content from Artifactory repository asynchronously.
     * 
     * @param repository the repository to fetch from
     * @param path the artifact path
     * @param proxyUserPass the proxy credentials
     * @return Uni with okhttp3.Response object (includes status, headers, body)
     * @throws GenericProxyException if validation fails
     */
    public Uni<Response> fetch(RemoteRepository repository, String path, UserPass proxyUserPass)
            throws GenericProxyException {
        validateProxyCredentials(proxyUserPass);
        String buildToken = resolveBuildToken(proxyUserPass);
        String user = resolveTrackingId(proxyUserPass);

        // Use WebClientAdapter-based content service - returns Uni<Response>
        // Pass null for HttpServerRequest since this is an internal call
        return contentService.doGet(buildToken, user, repository.getKey(), path, null);
    }

    /**
     * Resolve tracking ID from proxy credentials.
     * 
     * @param proxyUserPass the proxy credentials
     * @return tracking ID or null
     */
    public String resolveTrackingId(UserPass proxyUserPass) {
        if (proxyUserPass == null || proxyUserPass.getUser() == null) {
            return null;
        }

        return switch (proxyConfiguration.getTrackingType()) {
            case ALWAYS -> proxyUserPass.getUser();
            case STATIC -> artifactoryConfig.staticTrackingUser().orElse(null);
            case SUFFIX -> throw new UnsupportedOperationException("SUFFIX tracking not supported for Artifactory");
            case NEVER -> null;
        };
    }

    /**
     * Check if tracking is enabled for the given credentials.
     * 
     * @param proxyUserPass the proxy credentials
     * @return true if tracking is enabled
     */
    public boolean isTracking(UserPass proxyUserPass) {
        return switch (proxyConfiguration.getTrackingType()) {
            case ALWAYS -> proxyUserPass != null && proxyUserPass.getUser() != null;
            case STATIC -> true; // STATIC always supports tracking
            case SUFFIX -> throw new UnsupportedOperationException("SUFFIX tracking not supported for Artifactory");
            case NEVER -> false;
        };
    }

    /**
     * Validate proxy credentials before repository creation or content access.
     * This should be called early to fail fast if credentials are invalid.
     * 
     * @param proxyUserPass the proxy credentials to validate
     * @throws GenericProxyException if credentials are invalid or tracking requirements not met
     */
    public void validateCredentials(UserPass proxyUserPass) throws GenericProxyException {
        validateProxyCredentials(proxyUserPass);

        // Additional validation: check if build token can be resolved
        String buildToken = resolveBuildToken(proxyUserPass);
        String user = resolveTrackingId(proxyUserPass);

        // For ALWAYS and STATIC modes, we need a build token
        TrackingType trackingType = proxyConfiguration.getTrackingType();
        if ((trackingType == TrackingType.ALWAYS || trackingType == TrackingType.STATIC)
                && buildToken == null) {
            throw new GenericProxyException(
                    ApplicationStatus.BAD_REQUEST.code(),
                    "Tracking is enabled but no build token could be resolved from credentials.");
        }

        // External validation: test Build client credentials with Artifactory
        if (trackingType == TrackingType.ALWAYS || trackingType == TrackingType.STATIC) {
            boolean valid = contentService.validateBuildCredentials(buildToken, user);
            if (!valid) {
                throw new GenericProxyException(
                        ApplicationStatus.UNAUTHORIZED.code(),
                        "Build credentials validation failed for user: " + user);
            }
            logger.info("Build credentials validated successfully for user: {}", user);
        }

        logger.debug("Credentials validated for tracking type: {}", trackingType);
    }

    /**
     * Create repository in Artifactory.
     */
    private RemoteRepository createRepository(URL url, HttpRequest httpRequest, String repositoryName)
            throws GenericProxyException {
        UrlInfo urlInfo = new UrlInfo(url.toExternalForm());
        UserPass upstreamCredentials = UserPass.parse(ApplicationHeader.authorization, httpRequest, url.getAuthority());
        String baseUrl = getBaseUrl(url);

        try {
            // Create repository model
            RemoteRepository remote = new RemoteRepository(repositoryName, baseUrl);
            remote.setDescription("HTTProx proxy for: " + urlInfo.getUrl());
            remote.setTimeoutSeconds(300); // 5 minutes
            remote.setBypassHeadToGet(false);

            // Add metadata properties
            remote.addProperty("httprox.origin", "true");
            remote.addProperty("httprox.url", urlInfo.getUrl());

            // Create in Artifactory using Admin client
            RemoteRepository created = repositoryService.createRemoteRepository(remote);
            logger.info("Repository {} created in Artifactory", repositoryName);

            return created;
        } catch (RuntimeException e) {
            throw new GenericProxyException("Failed to create repository for {}", e, url);
        }
    }

    private String getRepositoryName(URL url) {
        return GenericRepositoryKey.forRemote(artifactoryConfig.projectKey(), url.getHost()).getName();
    }

    private String getBaseUrl(URL url) {
        int port = url.getPort();
        if (port < 1) {
            port = url.getDefaultPort();
        }

        String portSuffix = port != url.getDefaultPort() ? ":" + port : "";
        return String.format("%s://%s%s/", url.getProtocol(), url.getHost(), portSuffix);
    }

    private void validateProxyCredentials(UserPass proxyUserPass) throws GenericProxyException {
        TrackingType trackingType = proxyConfiguration.getTrackingType();

        if (trackingType == TrackingType.ALWAYS && (proxyUserPass == null || proxyUserPass.getUser() == null)) {
            throw new GenericProxyException(
                    ApplicationStatus.BAD_REQUEST.code(),
                    "Tracking is always-on, but no username was provided.");
        }

        if (trackingType == TrackingType.SUFFIX) {
            throw new UnsupportedOperationException("SUFFIX tracking not supported for Artifactory");
        }
    }

    private String resolveBuildToken(UserPass proxyUserPass) {
        return switch (proxyConfiguration.getTrackingType()) {
            case ALWAYS -> proxyUserPass != null ? proxyUserPass.getPassword() : null;
            case STATIC -> artifactoryConfig.staticTrackingPassword().orElse(null);
            case SUFFIX -> throw new UnsupportedOperationException("SUFFIX tracking not supported for Artifactory");
            case NEVER -> null;
        };
    }
}