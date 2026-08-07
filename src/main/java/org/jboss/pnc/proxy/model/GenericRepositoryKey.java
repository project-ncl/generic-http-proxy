/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.model;

import java.util.Objects;

/**
 * Simplified repository key for Artifactory repositories.
 * Format: packageType:name (e.g., "maven:NCL-gen-repo1-maven-org")
 */
public class GenericRepositoryKey {

    private final String name;

    public GenericRepositoryKey(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository name cannot be null or empty");
        }
        this.name = name;
    }

    /**
     * Create a repository key for a remote repository with standard naming.
     * Format: packageType:projectKey-gen-host
     *
     * @param projectKey the project key (e.g., "NCL")
     * @param host the sanitized host name
     * @return formatted GenericRepositoryKey
     */
    public static GenericRepositoryKey forRemote(String projectKey, String host) {
        String sanitizedHost = sanitizeHost(host);
        String name = projectKey + "-gen-" + sanitizedHost;
        return new GenericRepositoryKey(name);
    }

    /**
     * Sanitize a hostname for use in repository names.
     * Converts dots to dashes and removes special characters.
     * 
     * @param host the hostname to sanitize
     * @return sanitized hostname
     */
    private static String sanitizeHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        // Convert dots to dashes, remove special chars, lowercase
        return host.toLowerCase()
                .replaceAll("\\.", "-")
                .replaceAll("[^a-z0-9-]", "");
    }

    public String getPackageType() {
        return "generic";
    }

    public String getName() {
        return name;
    }

    /**
     * Get the full repository key in format: "packageType:name"
     */
    public String getKey() {
        return getPackageType() + ":" + name;
    }

    @Override
    public String toString() {
        return getKey();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GenericRepositoryKey that))
            return false;
        return Objects.equals(getName(), that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName());
    }
}
