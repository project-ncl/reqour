/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.openshift;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.Pod;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringSubstitutor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

@ApplicationScoped
public class PodDefinitionCreator {

    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @ConfigProperty(name = "reqour-rest.pod-definition-file")
    Path podDefinitionFilePath;

    public Pod getAdjusterPodDefinition() {
        Map<String, Object> properties = Map.of("adjustType", "GRADLE", "adjustRequest", "foo");
        final String resourceDefinition;
        try {
            resourceDefinition = FileUtils.readFileToString(podDefinitionFilePath.toFile(), StandardCharsets.UTF_8);
            String definition = StringSubstitutor.replace(resourceDefinition, properties, "%{", "}");
            return yamlMapper.readValue(definition, Pod.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
