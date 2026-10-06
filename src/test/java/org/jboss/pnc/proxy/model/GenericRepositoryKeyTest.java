/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.model;

import static org.jboss.pnc.proxy.model.GenericRepositoryKey.MAX_KEY_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GenericRepositoryKeyTest {

    private static final String PROJECT = "NCL";

    @Test
    void standardKeyUsesGenPrxInfix() {
        GenericRepositoryKey key = GenericRepositoryKey.forRemote(PROJECT, "repo1.maven.org");
        assertTrue(key.getName().startsWith("NCL-gen-prx-"), "Expected gen-prx infix, got: " + key.getName());
        assertEquals("NCL-gen-prx-repo1-maven-org", key.getName());
    }

    @Test
    void standardKeyIsWithinLengthLimit() {
        GenericRepositoryKey key = GenericRepositoryKey.forRemote(PROJECT, "repo1.maven.org");
        assertTrue(
                key.getName().length() <= MAX_KEY_LENGTH,
                "Key length " + key.getName().length() + " exceeds limit " + MAX_KEY_LENGTH);
    }

    @Test
    void dotsInHostAreConvertedToDashes() {
        GenericRepositoryKey key = GenericRepositoryKey.forRemote(PROJECT, "registry.npmjs.org");
        assertEquals("NCL-gen-prx-registry-npmjs-org", key.getName());
    }

    @Test
    void queryParamKeyUsesGenPrxQInfix() {
        GenericRepositoryKey key = GenericRepositoryKey.forRemoteWithQueryParams(PROJECT, "repo1.maven.org");
        assertTrue(key.getName().startsWith("NCL-gen-prx-q-"), "Expected gen-prx-q infix, got: " + key.getName());
        assertEquals("NCL-gen-prx-q-repo1-maven-org", key.getName());
    }

    @Test
    void queryParamKeyIsWithinLengthLimit() {
        GenericRepositoryKey key = GenericRepositoryKey.forRemoteWithQueryParams(PROJECT, "repo1.maven.org");
        assertTrue(
                key.getName().length() <= MAX_KEY_LENGTH,
                "Key length " + key.getName().length() + " exceeds limit " + MAX_KEY_LENGTH);
    }

    @Test
    void longHostTriggersHashTruncationForStandardKey() {
        String longHost = "a-very-long-subdomain.with-many-parts.at.some-deep.nested.enterprise.host.example.com";
        GenericRepositoryKey key = GenericRepositoryKey.forRemote(PROJECT, longHost);

        assertTrue(
                key.getName().length() <= MAX_KEY_LENGTH,
                "Key length " + key.getName().length() + " exceeds limit " + MAX_KEY_LENGTH);
        assertTrue(
                key.getName().matches(".*-[0-9a-f]{12}$"),
                "Expected MD5 suffix in: " + key.getName());
    }

    @Test
    void longHostTriggersHashTruncationForQueryParamKey() {
        String longHost = "a-very-long-subdomain.with-many-parts.at.some-deep.nested.enterprise.host.example.com";
        GenericRepositoryKey key = GenericRepositoryKey.forRemoteWithQueryParams(PROJECT, longHost);

        assertTrue(
                key.getName().length() <= MAX_KEY_LENGTH,
                "Key length " + key.getName().length() + " exceeds limit " + MAX_KEY_LENGTH);
        assertTrue(
                key.getName().matches(".*-[0-9a-f]{12}$"),
                "Expected MD5 suffix in: " + key.getName());
    }

    @Test
    void sameHostAlwaysProducesSameKey() {
        String longHost = "a-very-long-subdomain.with-many-parts.at.some-deep.nested.enterprise.host.example.com";
        String name1 = GenericRepositoryKey.forRemote(PROJECT, longHost).getName();
        String name2 = GenericRepositoryKey.forRemote(PROJECT, longHost).getName();
        assertEquals(name1, name2, "Same host must always produce the same key");
    }

    @Test
    void keyExactlyAtLimitRequiresNoHashSuffix() {
        // "NCL-gen-prx-" = 12 chars, so host must be 46 chars to hit exactly 58
        String host = "a".repeat(46);
        GenericRepositoryKey key = GenericRepositoryKey.forRemote(PROJECT, host);

        assertEquals(MAX_KEY_LENGTH, key.getName().length());
        assertTrue(
                key.getName().endsWith("a".repeat(46)),
                "Key should not have MD5 suffix when exactly at limit: " + key.getName());
    }

    @Test
    void trimAtDashBoundaryHardCutsWhenNoDashPresent() {
        String result = GenericRepositoryKey.trimAtDashBoundary("abcdefghij", 5);
        assertEquals("abcde", result);
    }

    @Test
    void trimAtDashBoundaryCutsAtLastDash() {
        // "abc-def-ghi" maxLen=8 → truncated to "abc-def-" → lastDash at index 7 → "abc-def"
        String result = GenericRepositoryKey.trimAtDashBoundary("abc-def-ghi", 8);
        assertEquals("abc-def", result);
    }

    @Test
    void trimAtDashBoundaryLeavesShortStringUnchanged() {
        String result = GenericRepositoryKey.trimAtDashBoundary("short", 10);
        assertEquals("short", result);
    }

    @Test
    void trimAtDashBoundaryHardCutsWhenDashIsAtPositionZero() {
        // lastDash == 0 is not > 0, so falls back to hard cut
        String result = GenericRepositoryKey.trimAtDashBoundary("-abcdefgh", 5);
        assertEquals("-abcd", result);
    }

    @Test
    void md5First12Returns12LowercaseHexChars() {
        String hash = GenericRepositoryKey.md5First12("repo1.maven.org");
        assertNotNull(hash);
        assertEquals(12, hash.length());
        assertTrue(hash.matches("[0-9a-f]{12}"), "Expected 12 lowercase hex chars, got: " + hash);
    }

    @Test
    void md5First12IsDeterministic() {
        assertEquals(
                GenericRepositoryKey.md5First12("repo1.maven.org"),
                GenericRepositoryKey.md5First12("repo1.maven.org"));
    }

    @Test
    void md5First12ProducesDifferentHashesForDifferentInputs() {
        String h1 = GenericRepositoryKey.md5First12("host.a.com");
        String h2 = GenericRepositoryKey.md5First12("host.b.com");
        assertNotEquals(h1, h2, "Different hosts should (very likely) produce different hashes");
    }
}
