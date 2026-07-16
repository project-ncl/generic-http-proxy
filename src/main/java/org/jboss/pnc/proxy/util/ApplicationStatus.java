/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.util;

public enum ApplicationStatus {

    /* @formatter:off */
    OK(200, "Ok"),
    CREATED(201, "Created"),
    NO_CONTENT(204, "No Content"),

    MOVED_PERMANENTLY(301, "Moved Permanently"),
    FOUND(302, "Found"),

    NOT_MODIFIED(304, "Not Modified"),

    BAD_REQUEST(400, "Bad Request"),

    UNAUTHORIZED(401, "Unauthorized"),

    GONE(410, "Gone"),

    FORBIDDEN(403, "Forbidden"),

    NOT_FOUND(404, "Not Found"),

    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),

    PROXY_AUTHENTICATION_REQUIRED(407, "Proxy Authentication Required"),

    CONFLICT(409, "Conflict"),

    SERVER_ERROR(500, "Internal Server Error"),

    NOT_IMPLEMENTED(501, "Not Implemented"),

    BAD_GATEWAY(502, "Bad Gateway");
    /* @formatter:on */

    private final int status;

    private final String message;

    ApplicationStatus(final int status, final String messsage) {
        this.status = status;
        this.message = messsage;
    }

    public static ApplicationStatus getStatus(final int status) {
        for (final ApplicationStatus as : values()) {
            if (as.code() == status) {
                return as;
            }
        }

        return null;
    }

    public int code() {
        return status;
    }

    public String message() {
        return message;
    }

}
