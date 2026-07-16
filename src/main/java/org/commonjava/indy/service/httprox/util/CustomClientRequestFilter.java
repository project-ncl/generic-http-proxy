/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.indy.service.httprox.util;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.oidc.client.OidcClient;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class CustomClientRequestFilter implements ClientRequestFilter {

    @Inject
    OidcClient client;

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders()
                .add(HttpHeaders.AUTHORIZATION, "Bearer " + client.getTokens().await().indefinitely().getAccessToken());
    }
}
