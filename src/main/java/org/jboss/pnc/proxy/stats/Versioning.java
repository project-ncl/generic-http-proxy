/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.stats;

import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Named;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Alternative
@Named
public class Versioning {

    private String version;

    private String builder;

    @JsonProperty("commit-id")
    private String commitId;

    private String timestamp;

    public Versioning() {
    }

    @JsonCreator
    public Versioning(
            @JsonProperty(value = "version") final String version,
            @JsonProperty("builder") final String builder,
            @JsonProperty("commit-id") final String commitId,
            @JsonProperty("timestamp") final String timestamp) {
        this.version = version;
        this.builder = builder;
        this.commitId = commitId;
        this.timestamp = timestamp;
    }

    public String getVersion() {
        return version;
    }

    public String getBuilder() {
        return builder;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getTimestamp() {
        return timestamp;
    }

}
