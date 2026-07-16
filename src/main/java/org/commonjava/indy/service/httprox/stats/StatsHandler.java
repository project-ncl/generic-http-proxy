/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.indy.service.httprox.stats;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("/api/stats")
public class StatsHandler {
    @Inject
    Versioning versioning;

    @Path("version-info")
    @GET
    @Produces(APPLICATION_JSON)
    public Response getAppVersion() {
        return formatOkResponseWithJsonEntity(versioning);
    }

    private Response formatOkResponseWithJsonEntity(final Object dto) {
        if (dto == null) {
            return Response.noContent().build();
        }
        Response.ResponseBuilder builder = Response.ok(dto, APPLICATION_JSON);
        return builder.build();
    }
}
