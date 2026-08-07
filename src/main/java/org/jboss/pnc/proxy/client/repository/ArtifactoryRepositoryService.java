/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.proxy.client.repository;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.proxy.client.Admin;
import org.jboss.pnc.proxy.config.ArtifactoryConfig;
import org.jboss.pnc.proxy.model.RemoteRepository;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.model.LightweightRepository;
import org.jfrog.artifactory.client.model.Repository;
import org.jfrog.artifactory.client.model.impl.RepositoryTypeImpl;
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for managing Artifactory repositories.
 * Handles creation, retrieval, and querying of remote repositories.
 */
@ApplicationScoped
public class ArtifactoryRepositoryService {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactoryRepositoryService.class);

    @Inject
    @Admin
    Artifactory artifactory;

    @Inject
    ArtifactoryConfig config;

    /**
     * Check if a repository exists in Artifactory.
     * 
     * @param repoKey the repository key
     * @return true if repository exists, false otherwise
     */
    public boolean repositoryExists(String repoKey) {
        try {
            Repository repo = artifactory.repository(repoKey).get();
            return repo != null;
        } catch (Exception e) {
            logger.debug("Repository {} does not exist: {}", repoKey, e.getMessage());
            return false;
        }
    }

    /**
     * Validate build credentials by attempting to access Artifactory.
     * 
     * @param buildToken the build token to validate
     * @return true if credentials are valid, false otherwise
     */
    public boolean validateCredentials(String buildToken) {
        // TODO: Implement credential validation
        // This would require creating a Build client with the token
        // and attempting a simple API call
        logger.warn("Credential validation not yet implemented");
        return true;
    }

    /**
     * Get a repository by key.
     * 
     * @param repoKey the repository key
     * @return RemoteRepository or null if not found
     */
    public RemoteRepository getRepository(String repoKey) {
        try {
            Repository repo = artifactory.repository(repoKey).get();
            if (repo instanceof org.jfrog.artifactory.client.model.RemoteRepository) {
                return mapToRemoteRepository(repo);
            }
            logger.warn("Repository {} is not a remote repository or does not exist", repoKey);
            return null;
        } catch (Exception e) {
            logger.error("Error retrieving repository {}: {}", repoKey, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Create a remote repository in Artifactory.
     * 
     * @param repo the RemoteRepository to create
     * @return the created RemoteRepository
     * @throws RuntimeException if creation fails
     */
    public RemoteRepository createRemoteRepository(RemoteRepository repo) {
        try {
            logger.info("Creating remote repository: {} with URL: {}", repo.getKey(), repo.getUrl());

            var genericRepository = new GenericRepositorySettingsImpl();

            // IMPORTANT: this forces Artifactory to make remote requests for folder paths
            genericRepository.setListRemoteFolderItems(true);

            org.jfrog.artifactory.client.model.RemoteRepository remoteRepo = artifactory.repositories()
                    .builders()
                    .remoteRepositoryBuilder()
                    .key(repo.getKey())
                    .url(repo.getUrl())
                    .description(repo.getDescription())
                    .listRemoteFolderItems(true)
                    .repositorySettings(genericRepository)
                    .projectKey(config.projectKey())
                    .build();

            String result = artifactory.repositories().create(2, remoteRepo);
            logger.info("Repository created successfully: {}, result: {}", repo.getKey(), result);

            return repo;
        } catch (Exception e) {
            logger.error("Error creating remote repository {}: {}", repo.getKey(), e.getMessage(), e);
            throw new RuntimeException("Failed to create remote repository: " + repo.getKey(), e);
        }
    }

    /**
     * List all remote repositories.
     * 
     * @return list of RemoteRepository objects
     */
    public List<RemoteRepository> listRemoteRepositories() {
        try {
            List<String> repoKeys = artifactory.repositories()
                    .list(RepositoryTypeImpl.REMOTE)
                    .stream()
                    .map(LightweightRepository::getKey)
                    .toList();

            return repoKeys.stream()
                    .map(key -> {
                        try {
                            Repository repo = artifactory.repository(key).get();
                            return mapToRemoteRepository(repo);
                        } catch (Exception e) {
                            logger.warn("Error retrieving repository {}: {}", key, e.getMessage());
                            return null;
                        }
                    })
                    .filter(repo -> repo != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error listing remote repositories: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Map Artifactory Repository to our RemoteRepository model.
     */
    private RemoteRepository mapToRemoteRepository(Repository repo) {
        if (!(repo instanceof org.jfrog.artifactory.client.model.RemoteRepository)) {
            return null;
        }

        org.jfrog.artifactory.client.model.RemoteRepository remoteRepo = (org.jfrog.artifactory.client.model.RemoteRepository) repo;

        RemoteRepository result = new RemoteRepository();
        result.setKey(remoteRepo.getKey());
        result.setUrl(remoteRepo.getUrl());
        result.setDescription(remoteRepo.getDescription());

        return result;
    }
}
