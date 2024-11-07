/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.openshift;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.Pod;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringSubstitutor;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.rest.config.ReqourRestConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@ApplicationScoped
public class PodDefinitionCreator {

    @Inject
    ObjectMapper objectMapper;

    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Inject
    ReqourRestConfig config;

    public Pod getAdjusterPodDefinition(AdjustRequest adjustRequest, String podName) {
        final Map<String, Object> properties;
        try {
            properties = Map.of(
                    "podName",
                    podName,
                    "buildType",
                    adjustRequest.getBuildType(),
                    "adjustRequest",
                    objectMapper.writeValueAsString(adjustRequest),
                    "appEnvironment",
                    config.appEnvironment(),
                    "reqourSecretKey",
                    config.reqourSecretKey(),
                    "indyUrl",
                    config.indyUrl()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        final String resourceDefinition;
        try {
            resourceDefinition = FileUtils
                    .readFileToString(config.podDefinitionFilePath().toFile(), StandardCharsets.UTF_8);
            String definition = StringSubstitutor.replace(resourceDefinition, properties, "%{", "}");
            return yamlMapper.readValue(definition, Pod.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
