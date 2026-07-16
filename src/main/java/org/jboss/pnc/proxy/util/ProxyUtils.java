/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.util;

import static io.vertx.core.http.impl.HttpUtils.normalizePath;

public class ProxyUtils {

    public static <R> R normalizePathAnd(String path, CheckedFunction<String, R> action) throws Exception {
        return action.apply(normalizePath(path));
    }

    @FunctionalInterface
    public interface CheckedFunction<T, R> {
        R apply(T t) throws Exception;
    }

}
