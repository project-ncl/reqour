/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.commonjava.maven.ext.common.json.PME;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.RemovedRepository;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.reqour.adjust.model.ExecutionRootOverrides;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.adjust.utils.CommonUtils;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Common extractor used from several {@link org.jboss.pnc.reqour.adjust.provider.AdjustProvider}s, typically when
 * obtaining the manipulator result.
 */
@ApplicationScoped
@Slf4j
public class CommonManipulatorResultExtractor {

    final static String REMOVED_REPOSITORIES_KEY = "-DrepoRemovalBackup";
    public final static String DEFAULT_REMOVED_REPOSITORIES_FILENAME = "repositories-backup.xml";

    private final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @UserLogger
    Logger userLogger;

    /**
     * Obtain {@link ManipulatorResult#getVersioningState()}. In case execution root overrides are specified, use them
     * for overrides.
     */
    public VersioningState obtainVersioningStateFromManipulatorResult(
            Path alignmentResultFilePath,
            ExecutionRootOverrides executionRootOverrides) {
        log.debug("Parsing versioning state from the file: '{}'", alignmentResultFilePath);
        IOUtils.validateResourceAtPathExists(
                alignmentResultFilePath,
                String.format(
                        "File which should contain alignment result ('%s') does not exist",
                        alignmentResultFilePath));

        try {
            PME manipulatorResult = objectMapper.readValue(alignmentResultFilePath.toFile(), PME.class);
            userLogger.info(
                    "Got manipulator result data: {}",
                    CommonUtils.prettyPrint(manipulatorResult));
            return transformPMEIntoVersioningState(manipulatorResult, executionRootOverrides);
        } catch (IOException e) {
            throw new RuntimeException("Could not deserialize the result of manipulator", e);
        }
    }

    private VersioningState transformPMEIntoVersioningState(
            PME manipulatorResult,
            ExecutionRootOverrides executionRootOverrides) {
        log.debug(
                "Transforming PME result into versioning state, version root overrides are: {}",
                executionRootOverrides);
        final String groupId;
        final String artifactId;

        if (executionRootOverrides.hasNoOverrides()) {
            groupId = manipulatorResult.getGav().getGroupId();
            artifactId = manipulatorResult.getGav().getArtifactId();
        } else {
            userLogger.warn("Overriding groupId as '{}'", executionRootOverrides.groupId());
            userLogger.warn("Overriding artifactId as '{}'", executionRootOverrides.artifactId());
            groupId = executionRootOverrides.groupId();
            artifactId = executionRootOverrides.artifactId();
        }

        return VersioningState.builder()
                .executionRootName(getExecutionRootName(groupId, artifactId))
                .executionRootVersion(manipulatorResult.getGav().getVersion())
                .build();
    }

    /**
     * Obtain {@link ManipulatorResult#getRemovedRepositories()}.
     */
    public List<RemovedRepository> obtainRemovedRepositories(Path workdir, List<String> manipulatorParameters) {
        log.debug("Obtaining removed repositories, manipulator parameters: {}", manipulatorParameters);

        Optional<String> repositoriesBackupFilename = AdjustmentSystemPropertiesUtils
                .getSystemPropertyValue(REMOVED_REPOSITORIES_KEY, manipulatorParameters.stream());

        final Path repositoriesBackupFile;
        if (repositoriesBackupFilename.isEmpty()) {
            userLogger.warn(
                    "No value for key '{}' found, will use the default filename for removed repositories list: {}",
                    REMOVED_REPOSITORIES_KEY,
                    DEFAULT_REMOVED_REPOSITORIES_FILENAME);
            repositoriesBackupFile = workdir.resolve(DEFAULT_REMOVED_REPOSITORIES_FILENAME);
        } else {
            userLogger.info("Parsing removed repositories from {}", repositoriesBackupFilename.get());
            repositoriesBackupFile = workdir.resolve(repositoriesBackupFilename.get());
        }

        if (Files.notExists(repositoriesBackupFile)) {
            userLogger.warn(
                    "File '{}' which should contain removed repositories does not exist. Hence, returning an empty list.",
                    repositoriesBackupFile);
            return Collections.emptyList();
        }

        try {
            log.debug("File with removed repositories: {}", Files.readString(repositoriesBackupFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<RemovedRepository> removedRepositories = new ArrayList<>();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document repositoriesParsed = db.parse(repositoriesBackupFile.toFile());
            NodeList repositories = repositoriesParsed.getElementsByTagName("repository");

            for (int i = 0; i < repositories.getLength(); i++) {
                Element repository = (Element) repositories.item(i);

                RemovedRepository.RemovedRepositoryBuilder builder = RemovedRepository.builder();
                builder.id(getRepositoryField(repository, "id"));
                builder.name(getRepositoryField(repository, "name"));
                builder.url(getRepositoryField(repository, "url"));
                builder.releases(true);
                builder.snapshots(true);

                NodeList enabledList = repository.getElementsByTagName("enabled");
                for (int j = 0; j < enabledList.getLength(); j++) {
                    if ("releases".equals(enabledList.item(j).getParentNode().getNodeName())) {
                        builder.releases(false);
                    }
                    if ("snapshots".equals(enabledList.item(j).getParentNode().getNodeName())) {
                        builder.snapshots(false);
                    }
                }

                removedRepositories.add(builder.build());
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new RuntimeException(
                    String.format(
                            "Cannot parse file '%s' which contains information about removed repositories",
                            repositoriesBackupFile),
                    e);
        }

        return removedRepositories;
    }

    private static String getExecutionRootName(String groupId, String artifactId) {
        return (groupId == null) ? artifactId : groupId + ":" + artifactId;
    }

    private static String getRepositoryField(Element repository, String field) {
        return repository.getElementsByTagName(field).item(0).getTextContent();
    }

    /**
     * Given the candidates for files with alignment results, pick the most desired one which exists.
     *
     * @param candidates list of candidates, sorted by preference in descending manner
     */
    public static Path getAlignmentResultsFilePath(Path... candidates) {
        for (var candidate : candidates) {
            log.debug("Trying to find the alignment results file: {}", candidate);
            if (Files.exists(candidate)) {
                log.debug("Found the alignment results file: {}", candidate);
                return candidate;
            }
        }

        throw new RuntimeException("No file with alignment results found");
    }
}
