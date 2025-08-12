/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import java.io.File;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.pnc.api.dto.GA;
import org.jboss.pnc.api.dto.GAV;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import lombok.extern.slf4j.Slf4j;

/**
 * Extractor of the {@code groupId}, {@code artifactId}, and {@code version} from the (effective) pom. <br/>
 * It is using <a href="https://maven.apache.org/plugins/maven-help-plugin/evaluate-mojo.html">Help maven plugin</a> for
 * doing so.
 */
@ApplicationScoped
@Slf4j
public class RootGavExtractor {

    /**
     * Extract the GAV from (effective) pom within the given working directory.
     *
     * @param workdir working directory
     */
    public GAV extractGav(Path workdir) {
        log.debug("Extracting GAV from POM in directory '{}'", workdir);
        File pom = workdir.resolve("pom.xml").toFile();
        String groupId = null;
        String artifactId = null;
        String version = null;

        try {
            groupId = getValueFromDotPath(pom, "project.groupId");
            artifactId = getValueFromDotPath(pom, "project.artifactId");
            version = getValueFromDotPath(pom, "project.version");

            if (groupId == null) {
                // read from parent groupId if project.groupId missing
                groupId = getValueFromDotPath(pom, "project.parent.groupId");
            }
            if (version == null) {
                // read from parent version if project.version missing
                version = getValueFromDotPath(pom, "project.parent.version");
            }
        } catch (Exception e) {
            log.error("Parsing of xml went wrong", e);
            throw new RuntimeException(e);
        }

        if (groupId == null || artifactId == null || version == null) {
            log.error(
                    "Parsing of pom.xml failed to get the required GAV: groupId: {}, artifactId: {}, version: {}",
                    groupId,
                    artifactId,
                    version);
            throw new RuntimeException("Parsing of pom.xml failed to get the required GAV");
        }
        return GAV.builder()
                .ga(
                        GA.builder()
                                .groupId(groupId)
                                .artifactId(artifactId)
                                .build())
                .version(version)
                .build();
    }

    public static String getValueFromDotPath(File xmlFile, String dotPath) throws Exception {
        String[] parts = dotPath.split("\\.");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        Node currentNode = doc.getDocumentElement();
        for (int i = 1; i < parts.length; i++) { // start from 1 because root already matched
            currentNode = getChildNodeByName(currentNode, parts[i]);
            if (currentNode == null) {
                return null;
            }
        }
        return currentNode.getTextContent().trim();
    }

    private static Node getChildNodeByName(Node parent, String name) {
        if (parent == null)
            return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeName().equals(name)) {
                return child;
            }
        }
        return null;
    }
}
