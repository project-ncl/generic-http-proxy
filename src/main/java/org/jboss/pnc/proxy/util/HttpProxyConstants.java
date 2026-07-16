/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

public final class HttpProxyConstants {
    public static final String PROXY_METRIC_LOGGER = "org.commonjava.topic.httprox.inbound";

    public static final String PROXY_REPO_PREFIX = "httprox_";

    public static final String GET_METHOD = "GET";

    public static final String HEAD_METHOD = "HEAD";

    public static final String CONNECT_METHOD = "CONNECT";

    public static final String OPTIONS_METHOD = "OPTIONS";

    public static final Set<String> ALLOWED_METHODS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(GET_METHOD, HEAD_METHOD, OPTIONS_METHOD)));

    public static final String ALLOW_HEADER_VALUE = StringUtils.join(ALLOWED_METHODS, ",");

    public static final String PROXY_AUTHENTICATE_FORMAT = "Basic realm=\"%s\"";

    public static final List<String> FORBIDDEN_HEADERS = Arrays
            .asList("content-length", "connection", "transfer-encoding");

    private HttpProxyConstants() {
    }

}
