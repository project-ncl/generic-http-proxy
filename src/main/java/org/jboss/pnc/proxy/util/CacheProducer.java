/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@ApplicationScoped
//TODO rewrite cache to be specifically for RemoteRepositories (or what Artifactory client has)
public class CacheProducer {

    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    public synchronized <K, V> Cache<K, V> getCache(String named) {
        return caches.computeIfAbsent(named, (k) -> buildCache());
    }

    private Cache buildCache() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .build();
        return cache;
    }

}
