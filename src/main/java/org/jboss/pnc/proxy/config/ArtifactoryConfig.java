/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for Artifactory client connection.
 * Uses token-based authentication for admin operations.
 * Supports static tracking credentials for STATIC tracking mode.
 */
@ConfigMapping(prefix = "artifactory")
public interface ArtifactoryConfig {

    /**
     * Artifactory server URL (e.g., http://localhost:8081/artifactory)
     */
    String url();

    /**
     * Artifactory access token for admin authentication
     */
    String accessToken();

    /**
     * Connection timeout in seconds
     */
    @WithDefault("3000")
    int connectionTimeout();

    /**
     * Socket timeout in seconds
     */
    @WithDefault("6000")
    int socketTimeout();

    /**
     * Project key for repository naming (e.g. "pnc-devel", "pnc")
     */
    @WithDefault("pnc")
    String projectKey();

    /**
     * Static tracking user for STATIC tracking mode.
     * Used when TrackingType.STATIC is configured.
     */
    Optional<String> staticTrackingUser();

    /**
     * Static tracking password/token for STATIC tracking mode.
     * Used when TrackingType.STATIC is configured.
     */
    Optional<String> staticTrackingPassword();
}