/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.indy.service.httprox.util;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

import org.apache.commons.io.IOUtils;

public final class PortFinder {
    private static final Random RANDOM = new Random();

    private PortFinder() {
    }

    public static <T> T findPortFor(int maxTries, PortConsumer<T> consumer) {
        for (int i = 0; i < maxTries; ++i) {
            int port = 1024 + Math.abs(RANDOM.nextInt()) % 30000;
            T result = null;

            try {
                return consumer.call(port);
            } catch (RuntimeException var6) {
                if (!var6.getMessage().contains("Address already in use")) {
                    throw var6;
                }
            } catch (IOException var7) {
            }
        }

        throw new IllegalStateException("Cannot find open port after " + maxTries + " attempts.");
    }

    public static int findOpenPort(int maxTries) {
        for (int i = 0; i < maxTries; ++i) {
            int port = 1024 + Math.abs(RANDOM.nextInt()) % 30000;
            ServerSocket sock = null;

            try {
                sock = new ServerSocket(port);
                int var4 = port;
                return var4;
            } catch (IOException var8) {
            } finally {
                IOUtils.closeQuietly(sock);
            }
        }

        throw new IllegalStateException("Cannot find open port after " + maxTries + " attempts.");
    }

    public interface PortConsumer<T> {
        T call(int var1) throws IOException;
    }
}
