/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.handler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Produces;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.proxy.config.ProxyConfiguration;

public class ProxyTransfersExecutor {

    @Inject
    ProxyConfiguration config;

    @Named("mitm-transfers")
    @ApplicationScoped
    @Produces
    public ManagedExecutor getExecutor() {

        return ManagedExecutor.builder()
                .maxAsync(config.getMitmMaxAsync())
                .maxQueued(config.getMitmMaxQueued())
                .build();
    }
}
