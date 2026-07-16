/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.util;

public enum ApplicationHeader {

    content_type("Content-Type"),
    location("Location"),
    uri("URI"),
    content_length("Content-Length"),
    last_modified("Last-Modified"),
    deprecated("Deprecated-Use-Alt"),
    accept("Accept"),
    allow("Allow"),
    authorization("Authorization"),
    proxy_authenticate("Proxy-Authenticate"),
    proxy_authorization("Proxy-Authorization"),
    cache_control("Cache-Control"),
    content_disposition("Content-Disposition"),
    indy_origin("Indy-Origin"),
    transfer_encoding("Transfer-Encoding"),
    md5("INDY-MD5"),
    sha1("INDY-SHA1");

    private final String key;

    ApplicationHeader(final String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String upperKey() {
        return key.toUpperCase();
    }

}
