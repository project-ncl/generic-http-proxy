/*
 * SPDX-FileCopyrightText: Copyright © 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Simplified repository key for Artifactory repositories.
 * <p>
 * Format: {@code <projectKey>-gen-prx-<host>} for standard repos, or {@code <projectKey>-gen-prx-q-<host>} for
 * query-parameter-aware repos.
 * <p>
 * The effective Artifactory key length limit is 58 characters (64 minus the 6-char {@code -cache} suffix that
 * Artifactory appends automatically for remote repos).
 * <p>
 * When the naive key would exceed that limit, the host slug is trimmed at a dash word-boundary and a 12-character MD5
 * hex suffix of the raw hostname is appended to preserve uniqueness.
 */
public class GenericRepositoryKey {

    /** Infix used for standard (non-query-param) remote repositories. */
    public static final String PROXY_REPO_INFIX = "-gen-prx-";

    /** Infix used for query-parameter-aware remote repositories. */
    public static final String PROXY_REPO_QUERY_INFIX = "-gen-prx-q-";

    /** Maximum safe Artifactory repository key length (64 - 6 for the {@code -cache} sibling). */
    static final int MAX_KEY_LENGTH = 58;

    private final String name;

    public GenericRepositoryKey(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository name cannot be null or empty");
        }
        this.name = name;
    }

    /**
     * Create a repository key for a standard remote repository.
     * Format: {@code <projectKey>-gen-prx-<sanitizedHost>} (length-safe).
     *
     * @param projectKey the project key (e.g., "NCL")
     * @param host the raw hostname (e.g., {@code "repo1.maven.org"})
     * @return a length-safe {@link GenericRepositoryKey}
     */
    public static GenericRepositoryKey forRemote(String projectKey, String host) {
        return new GenericRepositoryKey(buildSafeKey(projectKey, PROXY_REPO_INFIX, sanitizeHost(host), host));
    }

    /**
     * Create a repository key for a query-parameter-aware remote repository.
     * Format: {@code <projectKey>-gen-prx-q-<sanitizedHost>} (length-safe).
     *
     * @param projectKey the project key (e.g., "NCL")
     * @param host the raw hostname
     * @return a length-safe {@link GenericRepositoryKey}
     */
    public static GenericRepositoryKey forRemoteWithQueryParams(String projectKey, String host) {
        return new GenericRepositoryKey(buildSafeKey(projectKey, PROXY_REPO_QUERY_INFIX, sanitizeHost(host), host));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getPackageType() {
        return "generic";
    }

    public String getName() {
        return name;
    }

    /**
     * Get the full repository key in format: {@code "packageType:name"}
     */
    public String getKey() {
        return getPackageType() + ":" + name;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Build a repository key that is guaranteed to be at most {@value #MAX_KEY_LENGTH} characters.
     * <p>
     * If {@code projectKey + infix + sanitizedHost} fits within the limit, that string is returned
     * as-is. Otherwise the sanitized host is trimmed at the last dash within the available budget
     * and a 12-character MD5 hex suffix of the <em>raw</em> hostname is appended.
     *
     * @param projectKey the project key
     * @param infix the naming infix (e.g., {@code "-gen-prx-"})
     * @param sanitizedHost the host after {@link #sanitizeHost(String)}
     * @param rawHost the original, pre-sanitization hostname (used as MD5 input)
     * @return a repository name ≤ {@value #MAX_KEY_LENGTH} characters
     */
    private static String buildSafeKey(String projectKey, String infix, String sanitizedHost, String rawHost) {
        String candidate = projectKey + infix + sanitizedHost;
        if (candidate.length() <= MAX_KEY_LENGTH) {
            return candidate;
        }

        // Need to truncate: reserve room for "-<12-char-md5>"
        String hashSuffix = "-" + md5First12(rawHost); // 13 chars
        int availableForHost = MAX_KEY_LENGTH - projectKey.length() - infix.length() - hashSuffix.length();
        String trimmedHost = trimAtDashBoundary(sanitizedHost, availableForHost);
        return projectKey + infix + trimmedHost + hashSuffix;
    }

    /**
     * Sanitize a hostname for use in repository names.
     * Converts dots to dashes and removes non-alphanumeric/dash characters, lowercases.
     *
     * @param host the hostname to sanitize
     * @return sanitized hostname
     */
    private static String sanitizeHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        return host.toLowerCase().replaceAll("\\.", "-").replaceAll("[^a-z0-9-]", "");
    }

    /**
     * Trim {@code s} to at most {@code maxLen} characters, preferring to cut at the last {@code -}
     * within that length. Falls back to a hard cut at {@code maxLen} if no dash is present.
     *
     * @param s the string to trim
     * @param maxLen the maximum allowed length
     * @return trimmed string
     */
    static String trimAtDashBoundary(String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        String truncated = s.substring(0, maxLen);
        int lastDash = truncated.lastIndexOf('-');
        if (lastDash > 0) {
            return truncated.substring(0, lastDash);
        }
        return truncated;
    }

    /**
     * Compute the MD5 of {@code input} and return the first 12 lowercase hexadecimal characters.
     *
     * @param input the string to hash
     * @return 12-character lowercase hex prefix of the MD5 digest
     */
    static String md5First12(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toLowerCase().substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute MD5 for: " + input, e);
        }
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
