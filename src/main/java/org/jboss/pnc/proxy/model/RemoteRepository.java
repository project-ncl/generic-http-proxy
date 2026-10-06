/*
 * SPDX-FileCopyrightText: Copyright © 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Simplified remote repository model for Artifactory integration.
 * This model represents only remote (proxy) repositories, as the proxy service
 * does not require local or virtual repositories.
 */
public class RemoteRepository {

    private String key;
    private String url;
    private String description;
    private int timeoutSeconds;
    private boolean bypassHeadToGet;
    private Map<String, String> properties;
    private boolean propagateQueryParams;
    private boolean unusedArtifactsCleanupEnabled;
    private int unusedArtifactsCleanupPeriodHours;

    public RemoteRepository() {
        this.properties = new HashMap<>();
        this.timeoutSeconds = 300; // Default 5 minutes
        this.bypassHeadToGet = false;
        this.propagateQueryParams = false;
        this.unusedArtifactsCleanupEnabled = false;
        this.unusedArtifactsCleanupPeriodHours = 0;
    }

    public RemoteRepository(String key, String url) {
        this();
        this.key = key;
        this.url = url;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isBypassHeadToGet() {
        return bypassHeadToGet;
    }

    public void setBypassHeadToGet(boolean bypassHeadToGet) {
        this.bypassHeadToGet = bypassHeadToGet;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public void addProperty(String key, String value) {
        this.properties.put(key, value);
    }

    public boolean isPropagateQueryParams() {
        return propagateQueryParams;
    }

    public void setPropagateQueryParams(boolean propagateQueryParams) {
        this.propagateQueryParams = propagateQueryParams;
    }

    public boolean isUnusedArtifactsCleanupEnabled() {
        return unusedArtifactsCleanupEnabled;
    }

    public void setUnusedArtifactsCleanupEnabled(boolean unusedArtifactsCleanupEnabled) {
        this.unusedArtifactsCleanupEnabled = unusedArtifactsCleanupEnabled;
    }

    public int getUnusedArtifactsCleanupPeriodHours() {
        return unusedArtifactsCleanupPeriodHours;
    }

    public void setUnusedArtifactsCleanupPeriodHours(int unusedArtifactsCleanupPeriodHours) {
        this.unusedArtifactsCleanupPeriodHours = unusedArtifactsCleanupPeriodHours;
    }

    @Override
    public String toString() {
        return "RemoteRepository{" +
                "key='" + key + '\'' +
                ", url='" + url + '\'' +
                ", timeoutSeconds=" + timeoutSeconds +
                ", propagateQueryParams=" + propagateQueryParams +
                ", unusedArtifactsCleanupEnabled=" + unusedArtifactsCleanupEnabled +
                ", unusedArtifactsCleanupPeriodHours=" + unusedArtifactsCleanupPeriodHours +
                '}';
    }
}
