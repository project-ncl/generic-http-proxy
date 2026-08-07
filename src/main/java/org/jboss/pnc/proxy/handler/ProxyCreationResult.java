/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.handler;

import org.jboss.pnc.proxy.model.RemoteRepository;

/**
 * Simplified result for Artifactory repository creation.
 * Only contains remote repository - no hosted or group repositories needed.
 */
public class ProxyCreationResult {
    private RemoteRepository remote;

    public RemoteRepository getRemote() {
        return remote;
    }

    public void setRemote(RemoteRepository remote) {
        this.remote = remote;
    }

    /**
     * Get the repository key for the created remote repository.
     * 
     * @return repository key or null if no remote repository was created
     */
    public String getRepositoryKey() {
        return remote != null ? remote.getKey() : null;
    }
}
