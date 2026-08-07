/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.service.httprox;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Disabled //FIXME redo
public class ProxyHttpsTest extends AbstractGenericProxyTest {

    private static final String USER = "user";

    private static final String PASS = "password";

    final String httpsUrl = "https://oss.sonatype.org/content/repositories/releases/org/commonjava/indy/indy-api/1.3.1/indy-api-1.3.1.pom";

    @Test
    public void run() throws Exception {
        String ret = get(httpsUrl, true, USER, PASS);
        assertTrue(ret.contains("<artifactId>indy-api</artifactId>"));

    }

    final String httpsUrlWithQuery = "https://really.useful.script/org/test/simple.pom?version=2.0";

    @Test
    public void runWithQuery() throws Exception {
        String ret = get(httpsUrlWithQuery, true, USER, PASS);
        final String expected = loadResource("simple-2.0.pom");
        assertThat(ret, equalTo(expected));
    }
}
