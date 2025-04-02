/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.openshift;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringSubstitutor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.config.ReqourConfig;
import org.jboss.pnc.reqour.rest.config.ReqourRestConfig;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@ApplicationScoped
public class JobDefinitionCreator {

    @Inject
    ObjectMapper objectMapper;

    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Inject
    ReqourConfig reqourCoreConfig;

    @Inject
    ReqourRestConfig config;

    @ConfigProperty(name = "quarkus.oidc-client.credentials.secret")
    String saSecret;

    public Job getAdjusterJobDefinition(AdjustRequest adjustRequest, String jobName) {
        final Map<String, Object> properties;
        try {
            properties = Map.of(
                    "jobName",
                    jobName,
                    "buildType",
                    adjustRequest.getBuildType(),
                    "adjustRequest",
                    objectMapper.writeValueAsString(adjustRequest),
                    "appEnvironment",
                    config.appEnvironment(),
                    "reqourSecretKey",
                    config.reqourSecretKey(),
                    "indyUrl",
                    config.indyUrl(),
                    "bifrostUrl",
                    reqourCoreConfig.log().finalLog().bifrostUploader().baseUrl(),
                    "mdc",
                    objectMapper.writeValueAsString(MDC.getCopyOfContextMap()),
                    "saSecret",
                    saSecret,
                    "saslJaasConf",
                    config.saslJaasConf());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        final String resourceDefinition;
        try {
            resourceDefinition = FileUtils
                    .readFileToString(config.jobDefinitionFilePath().toFile(), StandardCharsets.UTF_8);
            String definition = StringSubstitutor.replace(resourceDefinition, properties, "%{", "}");
            return yamlMapper.readValue(definition, Job.class);
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse Job definition", e);
        }
    }
}
