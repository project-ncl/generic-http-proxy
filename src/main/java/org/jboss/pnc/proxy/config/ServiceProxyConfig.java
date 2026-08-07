/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.config;

import java.util.Optional;
import java.util.Set;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "service_proxy")
public interface ServiceProxyConfig {

    String readTimeout();

    Retry retry();

    Set<ServiceConfig> services();

    interface Retry {
        int count();

        long interval(); // in millis
    }

    interface ServiceConfig {
        String host();

        int port();

        boolean ssl();

        Optional<String> methods();

        String pathPattern();
    }

}
