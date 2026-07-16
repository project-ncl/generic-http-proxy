/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.service.httprox.client.mock;

import java.io.IOException;
import java.io.OutputStream;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.StreamingOutput;

import org.apache.commons.io.output.CountingOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DTOStreamingOutput
        implements StreamingOutput {

    private final ObjectMapper mapper;

    private final Object dto;

    public DTOStreamingOutput(final ObjectMapper mapper, final Object dto) {
        this.mapper = mapper;
        this.dto = dto;
    }

    @Override
    public String toString() {
        try {
            return mapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            Logger logger = LoggerFactory.getLogger(getClass());
            logger.error("Could not render toString() for DTO: " + dto, e);
            return String.valueOf(dto);
        }
    }

    @Override
    public void write(final OutputStream outputStream)
            throws IOException, WebApplicationException {

        CountingOutputStream cout = new CountingOutputStream(outputStream);
        try {
            mapper.writeValue(cout, dto);
        } catch (IOException e) {
            throw e;
        }

    }
}
