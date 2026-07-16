/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public interface HttpWrapper
        extends Closeable {
    void writeError(Throwable e)
            throws IOException;

    void writeHeader(ApplicationHeader header, String value)
            throws IOException;

    void writeHeader(String header, String value)
            throws IOException;

    void writeStatus(ApplicationStatus status)
            throws IOException;

    void writeStatus(int code, String message)
            throws IOException;

    boolean isOpen();

    List<String> getHeaders(String name);
}
