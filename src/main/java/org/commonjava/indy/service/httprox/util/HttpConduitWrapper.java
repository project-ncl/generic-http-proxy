/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.indy.service.httprox.util;

import static org.commonjava.indy.service.httprox.util.ChannelUtils.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.commonjava.indy.model.core.ArtifactStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSinkChannel;

import okhttp3.Headers;

public class HttpConduitWrapper
        implements org.commonjava.indy.service.httprox.util.HttpWrapper {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StreamSinkChannel sinkChannel;

    private final HttpRequest httpRequest;

    public HttpConduitWrapper(StreamSinkChannel channel, HttpRequest httpRequest) {
        this.sinkChannel = channel;
        this.httpRequest = httpRequest;
    }

    @Override
    public void writeError(final Throwable e)
            throws IOException {
        final String message = String.format("%s:\n  %s", e.getMessage(), StringUtils.join(e.getStackTrace(), "\n  "));
        logger.warn("Write errors: {}", message);
        sinkChannel.write(ByteBuffer.wrap(message.getBytes()));
    }

    @Override
    public void writeHeader(final ApplicationHeader header, final String value)
            throws IOException {
        final ByteBuffer b = ByteBuffer.wrap(String.format("%s: %s\r\n", header.key(), value).getBytes());
        sinkChannel.write(b);
    }

    @Override
    public void writeHeader(final String header, final String value)
            throws IOException {
        final ByteBuffer b = ByteBuffer.wrap(String.format("%s: %s\r\n", header, value).getBytes());
        sinkChannel.write(b);
    }

    @Override
    public void writeStatus(final ApplicationStatus status)
            throws IOException {
        final ByteBuffer b = ByteBuffer
                .wrap(String.format("HTTP/1.1 %d %s\r\n", status.code(), status.message()).getBytes());
        sinkChannel.write(b);
    }

    @Override
    public void writeStatus(final int code, final String message)
            throws IOException {
        final ByteBuffer b = ByteBuffer.wrap(String.format("HTTP/1.1 %d %s\r\n", code, message).getBytes());
        sinkChannel.write(b);
    }

    public void writeClose()
            throws IOException {
        writeHeader("Connection", "close\r\n");
    }

    public void writeExistingTransfer(InputStream txfr, boolean writeBody, Headers headers)
            throws IOException {
        Logger logger = LoggerFactory.getLogger(getClass());
        logger.debug("Valid transfer found, {}", txfr);

        AtomicBoolean chunked = new AtomicBoolean(false);
        try {

            writeStatus(ApplicationStatus.OK);

            headers.forEach(header -> {
                logger.debug("Setting response header: {} = {}", header.getFirst(), header.getSecond());
                try {
                    if (header.getFirst().equalsIgnoreCase(ApplicationHeader.content_length.key())
                            || header.getFirst().equalsIgnoreCase(ApplicationHeader.last_modified.key())
                            || header.getFirst().equalsIgnoreCase(ApplicationHeader.content_type.key())
                            || header.getFirst().equalsIgnoreCase(ApplicationHeader.transfer_encoding.key())) {
                        writeHeader(header.getFirst(), header.getSecond());
                    }

                    if (header.getFirst().equalsIgnoreCase(ApplicationHeader.transfer_encoding.key())
                            && header.getSecond().equalsIgnoreCase("chunked")) {
                        chunked.set(true);
                    }
                } catch (IOException e) {
                    logger.error("Write header error: {}", e.getMessage(), e);
                }
            });

            writeHeader("Connection", "close");

            logger.trace("Write body, {}", writeBody);
            if (writeBody) {
                sinkChannel.write(ByteBuffer.wrap("\r\n".getBytes()));

                int capacity = DEFAULT_READ_BUF_SIZE;
                ByteBuffer bbuf = ByteBuffer.allocate(capacity);
                byte[] buf = new byte[capacity];
                int read = -1;
                logger.trace("Read transfer...");
                while ((read = txfr.read(buf)) > -1) {
                    if (chunked.get()) {
                        // writes the chunk size (in hexadecimal)
                        sinkChannel.write(ByteBuffer.wrap((Integer.toHexString(read) + "\r\n").getBytes()));
                    }

                    logger.trace("Read transfer and write to channel, size: {}", read);
                    bbuf.clear();
                    bbuf.put(buf, 0, read);
                    bbuf.flip();
                    write(sinkChannel, bbuf);

                    if (chunked.get()) {
                        // writes the chunk data followed by \r\n.
                        sinkChannel.write(ByteBuffer.wrap("\r\n".getBytes()));
                    }
                }

                if (chunked.get()) {
                    // Write the final chunk (0-length) to signal the end of the response
                    sinkChannel.write(ByteBuffer.wrap("0\r\n\r\n".getBytes()));
                }
            }
        } finally {
            //TODO
        }
        sinkChannel.flush();
        logger.debug("Write transfer DONE.");
    }

    public void writeNotFoundTransfer(ArtifactStore store, String path)
            throws IOException {
        Logger logger = LoggerFactory.getLogger(getClass());
        logger.debug("No transfer found.");

        writeStatus(ApplicationStatus.NOT_FOUND);

        writeClose();
    }

    @Override
    public boolean isOpen() {
        return sinkChannel != null && sinkChannel.isOpen();
    }

    @Override
    public List<String> getHeaders(String name) {
        List<String> result = new ArrayList<>();
        Header[] headers = httpRequest.getHeaders(name);
        for (final Header header : headers) {
            result.add(header.getValue());
        }

        return result;
    }

    public void close() throws IOException {
        if (isOpen()) {
            flush(sinkChannel);
            sinkChannel.shutdownWrites();
            sinkChannel.close();
        }
    }
}
