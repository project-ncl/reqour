/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.openshift;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringSubstitutor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.config.core.ConfigConstants;
import org.jboss.pnc.reqour.config.core.ReqourConfig;
import org.jboss.pnc.reqour.config.rest.ReqourRestConfig;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;
import org.slf4j.MDC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class JobDefinitionCreator {

    @Inject
    ObjectMapper objectMapper;

    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Inject
    ReqourConfig reqourCoreConfig;

    @Inject
    ReqourConfig coreConfig;

    @Inject
    ReqourRestConfig config;

    @ConfigProperty(name = ConfigConstants.OIDC_CLIENT_SECRET)
    String saSecret;

    @Inject
    @UserLogger
    Logger userLogger;

    private static final double RESOURCES_MEMORY_DEFAULT = 4d;

    public Job getAdjusterJobDefinition(AdjustRequest adjustRequest, String jobName) {
        final Map<String, Object> properties = new HashMap<>();

        final String privateGithubUser = coreConfig.gitConfigs().privateGithubUser().isEmpty() ? ""
                : coreConfig.gitConfigs().privateGithubUser().get();
        try {
            properties.put("jobName", jobName);
            properties.put("buildType", adjustRequest.getBuildType());
            properties.put("adjustRequest", prepareAdjustRequest(adjustRequest));
            properties.put("appEnvironment", config.appEnvironment());
            properties.put("resourcesMemory", getResourcesMemory(adjustRequest.getBuildConfigParameters()));
            properties.put("reqourSecretKey", config.reqourSecretKey());
            properties.put("indyUrl", config.indyUrl());
            properties.put("bifrostUrl", reqourCoreConfig.log().finalLog().bifrostUploader().baseUrl());
            properties.put("mdc", objectMapper.writeValueAsString(MDC.getCopyOfContextMap()));
            properties.put("saSecret", saSecret);
            properties.put("saslJaasConf", config.saslJaasConf());
            properties.put("privateGithubUser", privateGithubUser);
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

    String getResourcesMemory(Map<BuildConfigurationParameterKeys, String> buildConfigParameters) {
        String podMemorySizeFromDefault = getPodMemoryString(RESOURCES_MEMORY_DEFAULT);
        if (buildConfigParameters == null
                || !buildConfigParameters.containsKey(BuildConfigurationParameterKeys.ALIGNMENT_POD_MEMORY)) {
            userLogger.info(
                    "No override for alignment pod memory size provided, hence, using the default: {}",
                    podMemorySizeFromDefault);
            return podMemorySizeFromDefault;
        }

        String podMemorySizeOverride = buildConfigParameters.get(BuildConfigurationParameterKeys.ALIGNMENT_POD_MEMORY);
        try {
            double parsedPodMemory = Double.parseDouble(podMemorySizeOverride);
            if (parsedPodMemory > 0) {
                String podMemorySize = getPodMemoryString(parsedPodMemory);
                userLogger.info(
                        "Using override '{}' for alignment pod memory size, which will be: {}",
                        podMemorySizeOverride,
                        podMemorySize);
                return podMemorySize;
            } else {
                userLogger.info(
                        "Overridden alignment memory size cannot have negative value, hence, using the default: {}",
                        podMemorySizeFromDefault);
                return podMemorySizeFromDefault;
            }
        } catch (NumberFormatException ex) {
            userLogger.warn(
                    "Failed to parse memory size '{}', hence, using the default: {}",
                    podMemorySizeOverride,
                    podMemorySizeFromDefault);
            return podMemorySizeFromDefault;
        }
    }

    private String getPodMemoryString(double podMemory) {
        return ((int) Math.ceil(podMemory * 1024)) + "Mi";
    }

    String prepareAdjustRequest(AdjustRequest adjustRequest) throws JsonProcessingException {
        return objectMapper.writeValueAsString(adjustRequest);
    }
}
