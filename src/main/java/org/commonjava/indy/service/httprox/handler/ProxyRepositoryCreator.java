/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.indy.service.httprox.handler;

import org.commonjava.indy.service.httprox.util.UrlInfo;
import org.commonjava.indy.service.httprox.util.UserPass;
import org.slf4j.Logger;

public interface ProxyRepositoryCreator {
    /**
     * It creates a normal remote repository when trackingID is null. It creates group, hosted, and remote
     * when trackingID != null.
     * 1. hosted repo allows any content
     * 2. remote repo looks just like in the trackingID == null case, but with passthrough flag set to false
     * 3. group contains members hosted and remote
     *
     * @param trackingID
     * @param name result of formatId. It is not used when trackingID is given
     * @param baseUrl
     * @param urlInfo
     * @param userPass
     * @param logger
     * @return ProxyCreationResult containing the remote repo or (group, remote, hosted)
     */
    ProxyCreationResult create(
            String trackingID,
            String name,
            String baseUrl,
            UrlInfo urlInfo,
            UserPass userPass,
            Logger logger);

    /**
     * Format repo names. By default, when trackingId is null, it returns "httproxy_host_port_index".
     * When trackingId is given, it return 'g/h/r-<upstream-hostname>-<trackingID>' for group/hosted/remote
     * respectively.
     *
     * @param host upstream hostname
     * @param port upstream port
     * @param index appended when the repository name already exists
     * @param trackingID
     * @param storeType group, remote, or hosted
     * @return
     */
    String formatId(String host, int port, int index, String trackingID, String storeType);
}
