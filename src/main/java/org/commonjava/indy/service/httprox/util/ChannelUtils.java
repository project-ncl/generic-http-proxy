/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.indy.service.httprox.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSinkChannel;

public class ChannelUtils {
    public static final int DEFAULT_READ_BUF_SIZE = 1024 * 32;
    private static final Logger logger = LoggerFactory.getLogger(ChannelUtils.class);
    private static final int MAX_FLUSH_RETRY_COUNT = 3;

    private static final int MAX_WRITE_RETRY_COUNT = 10;

    public static void flush(StreamSinkChannel channel) throws IOException {
        int retry = 0;
        boolean flushed = false;
        while (!flushed) {
            flushed = channel.flush();
            retry++;
            if (retry >= MAX_FLUSH_RETRY_COUNT) {
                logger.debug("Retry {} times and fail...", retry);
                break;
            }
            if (!flushed) {
                wait(100);
            }
        }
    }

    public static void write(WritableByteChannel channel, ByteBuffer bbuf) throws IOException {
        int retry = 0;
        while (bbuf.hasRemaining() && retry < MAX_WRITE_RETRY_COUNT) {
            int written = channel.write(bbuf);

            if (written == 0) {
                // The channel is non-blocking and couldn't write anything at the moment.
                wait(100);
                retry++;
            } else {
                retry = 0; // Reset retry if some data was successfully written
            }
        }

        if (retry >= MAX_WRITE_RETRY_COUNT) {
            throw new IOException("Exceeded maximum write retry.");
        }
    }

    private static void wait(int milliseconds) {
        logger.debug("Waiting for channel to flush...");
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
