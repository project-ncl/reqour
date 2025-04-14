/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.openshift;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.AlignmentPreference;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.InternalGitRepositoryUrl;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.profile.WithStaticBifrostUrl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(WithStaticBifrostUrl.class) // override bifrost URL just for this test
class JobDefinitionCreatorTest {

    @Inject
    JobDefinitionCreator jobDefinitionCreator;

    private static final String ADJUSTER_JOB_NAME = "adjusterJob";
    private static final Map<String, String> MDC_MAP = Map.of("foo", "bar");

    @Inject
    ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        MDC.setContextMap(MDC_MAP);
    }

    @Test
    void getAdjusterJobDefinition() throws JsonProcessingException {
        // Arrange
        Quantity expectedAdjusterPodMemory = Quantity.parse("12288Mi");
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readonlyUrl("https://gitlab.com/org/project.git")
                                .readonlyUrl("git@gitlab.com:org/project.git")
                                .build())
                .ref("foo")
                .callback(
                        Request.builder()
                                .method(Request.Method.POST)
                                .uri(URI.create("https://callback.example.com"))
                                .build())
                .originRepoUrl("https://github.com/org/project.git")
                .tempBuild(false)
                .alignmentPreference(AlignmentPreference.PREFER_PERSISTENT)
                .buildConfigParameters(Map.of(BuildConfigurationParameterKeys.ALIGNMENT_POD_MEMORY, "12"))
                .buildType(BuildType.MVN)
                .brewPullActive(true)
                .taskId(TestDataSupplier.TASK_ID)
                .build();

        // Act
        Job adjusterJobDefinition = jobDefinitionCreator.getAdjusterJobDefinition(adjustRequest, ADJUSTER_JOB_NAME);

        // Assert
        assertThat(adjusterJobDefinition).isNotNull();
        assertThat(adjusterJobDefinition.getMetadata().getName()).isEqualTo(ADJUSTER_JOB_NAME);

        List<EnvVar> envVars = adjusterJobDefinition.getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .getLast()
                .getEnv();
        assertThat(envVars.get(0).getName()).isEqualTo("KAFKA_CLIENT_SECRET_NAME");
        assertThat(envVars.get(0).getValue()).isEqualTo("kafka-client-secret");

        assertThat(envVars.get(1).getName()).isEqualTo("REQOUR_SECRET_NAME");
        assertThat(envVars.get(1).getValue()).isEqualTo("test-secret");

        assertThat(envVars.get(2).getName()).isEqualTo("APP_ENV");
        assertThat(envVars.get(2).getValue()).isEqualTo("test");

        assertThat(envVars.get(3).getName()).isEqualTo("INDY_URL");
        assertThat(envVars.get(3).getValue()).isEqualTo("https://test.indy.com");

        assertThat(envVars.get(4).getName()).isEqualTo("BIFROST_URL");
        assertThat(envVars.get(4).getValue()).isEqualTo("https://test.bifrost.com");

        assertThat(envVars.get(5).getName()).isEqualTo("BUILD_TYPE");
        assertThat(envVars.get(5).getValue()).isEqualTo(adjustRequest.getBuildType().toString());

        assertThat(envVars.get(6).getName()).isEqualTo("ADJUST_REQUEST");
        assertThat(envVars.get(6).getValue()).contains(objectMapper.writeValueAsString(adjustRequest));

        assertThat(envVars.get(7).getName()).isEqualTo("MDC");
        assertThat(envVars.get(7).getValue()).isEqualTo(objectMapper.writeValueAsString(MDC_MAP));

        assertThat(envVars.get(8).getName()).isEqualTo("OIDC_CLIENT_CREDENTIALS_SECRET");
        assertThat(envVars.get(8).getValue()).isEqualTo("oidc-client-secret");

        assertThat(envVars.get(9).getName()).isEqualTo("SASL_JAAS_CONF");
        assertThat(envVars.get(9).getValue()).isEqualTo("sasl-jaas-config");

        assertThat(
                adjusterJobDefinition.getSpec()
                        .getTemplate()
                        .getSpec()
                        .getContainers()
                        .getLast()
                        .getResources()
                        .getLimits()
                        .get("memory"))
                .isEqualTo(expectedAdjusterPodMemory);
        assertThat(
                adjusterJobDefinition.getSpec()
                        .getTemplate()
                        .getSpec()
                        .getContainers()
                        .getLast()
                        .getResources()
                        .getRequests()
                        .get("memory"))
                .isEqualTo(expectedAdjusterPodMemory);
    }

    @Test
    void getResourcesMemory_buildConfigsNotPresent_usesDefault() {
        Map<BuildConfigurationParameterKeys, String> buildConfigParameters = null;
        String expectedAdjusterMemory = "4096Mi";

        assertThat(jobDefinitionCreator.getResourcesMemory(buildConfigParameters)).isEqualTo(expectedAdjusterMemory);
    }

    @Test
    void getResourcesMemory_buildConfigsPresentButNoAdjusterMemoryOverride_usesDefault() {
        Map<BuildConfigurationParameterKeys, String> buildConfigParameters = Map
                .of(BuildConfigurationParameterKeys.BUILDER_POD_MEMORY, "12");
        String expectedAdjusterMemory = "4096Mi";

        assertThat(jobDefinitionCreator.getResourcesMemory(buildConfigParameters)).isEqualTo(expectedAdjusterMemory);
    }

    @Test
    void getResourcesMemory_buildConfigsPresentWithInvalidAdjusterMemoryOverride_usesDefault() {
        Map<BuildConfigurationParameterKeys, String> buildConfigParameters = Map
                .of(BuildConfigurationParameterKeys.ALIGNMENT_POD_MEMORY, "-12");
        String expectedAdjusterMemory = "4096Mi";

        assertThat(jobDefinitionCreator.getResourcesMemory(buildConfigParameters)).isEqualTo(expectedAdjusterMemory);
    }

    @Test
    void getResourcesMemory_buildConfigsPresentWithValidAdjusterMemoryOverride_usesOverride() {
        Map<BuildConfigurationParameterKeys, String> buildConfigParameters = Map
                .of(BuildConfigurationParameterKeys.ALIGNMENT_POD_MEMORY, "12");
        String expectedAdjusterMemory = "12288Mi";

        assertThat(jobDefinitionCreator.getResourcesMemory(buildConfigParameters)).isEqualTo(expectedAdjusterMemory);
    }
}
