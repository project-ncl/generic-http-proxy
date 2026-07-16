/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.indy.service.httprox.util;

import static org.apache.commons.lang3.StringUtils.join;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UrlUtils {
    private UrlUtils() {
    }

    public static String buildUrl(final String baseUrl, final String... parts) {
        return buildUrl(baseUrl, null, parts);
    }

    public static String buildUrl(
            final String baseUrl,
            final Supplier<Map<String, String>> paramSupplier,
            final String... parts) {
        Logger logger = LoggerFactory.getLogger(UrlUtils.class);
        if (logger.isDebugEnabled()) {
            logger.debug("Creating url from base: '{}' and parts: {}", baseUrl, join(parts, ", "));
        }

        if (parts == null || parts.length < 1) {
            return baseUrl;
        }

        final StringBuilder urlBuilder = new StringBuilder();

        final List<String> list = new ArrayList<>();

        if (baseUrl != null && !"null".equals(baseUrl)) {
            list.add(baseUrl);
        } else {
            list.add("/");
        }

        for (final String part : parts) {
            if (part == null || "null".equals(part)) {
                continue;
            }

            list.add(part);
        }

        urlBuilder.append(normalizePath(list.toArray(new String[list.size()])));

        if (paramSupplier != null) {
            Map<String, String> params = paramSupplier.get();

            urlBuilder.append("?");
            boolean first = true;
            for (final Map.Entry<String, String> param : params.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    urlBuilder.append("&");
                }

                urlBuilder.append(param.getKey()).append("=").append(param.getValue());
            }
        }

        return urlBuilder.toString();
    }

    public static String normalizePath(final String... path) {
        if (path == null || path.length < 1) {
            return "/";
        }

        final StringBuilder sb = new StringBuilder();
        int idx = 0;
        parts: for (String part : path) {
            if (part == null || part.length() < 1 || "/".equals(part)) {
                continue;
            }

            if (idx == 0 && part.startsWith("file:")) {
                if (part.length() > 5) {
                    sb.append(part.substring(5));
                }

                continue;
            }

            if (idx > 0) {
                while (part.charAt(0) == '/') {
                    if (part.length() < 2) {
                        continue parts;
                    }

                    part = part.substring(1);
                }
            }

            while (part.charAt(part.length() - 1) == '/') {
                if (part.length() < 2) {
                    continue parts;
                }

                part = part.substring(0, part.length() - 1);
            }

            if (sb.length() > 0) {
                sb.append('/');
            }

            sb.append(part);
            idx++;
        }

        if (path[path.length - 1].endsWith("/")) {
            sb.append("/");
        }

        return sb.toString();
    }

    /**
     * Encode path using a URL-safe base64 algorithm if there are query parameters.
     */
    public static String base64url(String path) {
        return Base64.encodeBase64URLSafeString(path.getBytes(StandardCharsets.UTF_8));
    }

}
