/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.service.httprox;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ProxyHttpsNotFoundTest extends AbstractGenericProxyTest {

    private static final String USER = "user";

    private static final String PASS = "password";

    String https_url = "https://oss.sonatype.org/content/repositories/releases/org/commonjava/indy/indy-api/no.pom";

    @Test
    public void run() throws Exception {
        String ret = get(https_url, true, USER, PASS);
        assertTrue(ret.contains("404 Not Found"));
    }

}
