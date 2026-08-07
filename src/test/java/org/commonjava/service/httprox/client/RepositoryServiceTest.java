/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.service.httprox.client;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.commonjava.indy.model.core.*;
import org.commonjava.indy.model.core.dto.StoreListingDTO;
import org.commonjava.indy.model.core.io.IndyObjectMapper;
import org.commonjava.indy.pkg.PackageTypeConstants;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.pnc.proxy.client.repository.RepositoryService;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class RepositoryServiceTest {

    @Inject
    @RestClient
    RepositoryService repositoryService;

    //@Test
    public void testArtifactStoreExists() {
        repositoryService.repoExists("maven", "hosted", "pnc-builds");
    }

    //@Test
    public void testCreateStore() throws JsonProcessingException {

        HostedRepository hostedRepository = new HostedRepository(
                PackageTypeConstants.PKG_TYPE_GENERIC_HTTP,
                "test-generic-0001");
        hostedRepository.setDescription("test creating hosted via REST");
        hostedRepository.setPathStyle(PathStyle.hashed);

        repositoryService.createStore(
                PackageTypeConstants.PKG_TYPE_GENERIC_HTTP,
                "hosted",
                new IndyObjectMapper(false).writeValueAsString(hostedRepository));
    }

    //@Test
    public void testGetRemoteByUrl() {
        Response response = repositoryService
                .getRemoteByUrl(PackageTypeConstants.PKG_TYPE_GENERIC_HTTP, "remote", "http://download.jboss.org:80/");
        StoreListingDTO<RemoteRepository> dto = response.readEntity(StoreListingDTO.class);
        for (RemoteRepository remoteRepository : dto.getItems()) {
            System.out.println(remoteRepository.getName());
        }
    }

    //@Test
    public void testGetArtifactStore() {
        Response response = repositoryService
                .getStore(PackageTypeConstants.PKG_TYPE_GENERIC_HTTP, "group", "g-fasterxml-github-com-build-35505");
        System.out.println(response.getStatus());
        ArtifactStore artifactStore = response.readEntity(ArtifactStore.class);
        System.out.println(artifactStore.getName());

    }

}
