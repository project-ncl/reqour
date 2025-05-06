/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import static org.jboss.pnc.api.constants.HttpHeaders.CONTENT_TYPE_STRING;
import static org.jboss.pnc.reqour.common.TestDataSupplier.BIFROST_FINAL_LOG_UPLOAD_PATH;
import static org.jboss.pnc.reqour.common.TestDataSupplier.CALLBACK_PATH;

import java.util.Collections;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.AdjustResponse;
import org.jboss.pnc.api.reqour.dto.InternalGitRepositoryUrl;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.common.log.ProcessStageUtils;
import org.jboss.pnc.reqour.adjust.profile.WithSuccessfulAlternatives;
import org.jboss.pnc.reqour.enums.AdjustProcessStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(WithSuccessfulAlternatives.class)
@ConnectWireMock
class AlignmentSuccessTest {

    @Inject
    @TopCommand
    App app;

    WireMock wireMock;

    @Inject
    AdjustTestUtils adjustTestUtils;

    @BeforeEach
    void setUp() {
        wireMock.register(WireMock.post(BIFROST_FINAL_LOG_UPLOAD_PATH).willReturn(WireMock.ok()));
        wireMock.register(WireMock.post(CALLBACK_PATH).willReturn(WireMock.ok()));
        wireMock.register(WireMock.post(adjustTestUtils.getHeartbeatPath()).willReturn(WireMock.ok()));
    }

    @Inject
    ObjectMapper objectMapper;

    @Test
    void run_alignmentSuccessful_finalLogAndCallbackSent() throws InterruptedException, JsonProcessingException {
        app.run();

        Thread.sleep(2_000);
        wireMock.verifyThat(
                1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(BIFROST_FINAL_LOG_UPLOAD_PATH))
                        .withRequestBody(
                                WireMock.and(
                                        AdjustTestUtils.getWireMockContainingPredicate(
                                                ProcessStageUtils.Step.END,
                                                AdjustProcessStage.STARTING_ALIGNMENT_POD),
                                        AdjustTestUtils.getWireMockContainingPredicate(
                                                ProcessStageUtils.Step.BEGIN,
                                                AdjustProcessStage.SCM_CLONE),
                                        WireMock.containing("[INFO] Cloning a repository"),
                                        AdjustTestUtils.getWireMockContainingPredicate(
                                                ProcessStageUtils.Step.END,
                                                AdjustProcessStage.SCM_CLONE),
                                        AdjustTestUtils.getWireMockContainingPredicate(
                                                ProcessStageUtils.Step.BEGIN,
                                                AdjustProcessStage.ALIGNMENT),
                                        WireMock.containing(
                                                "[INFO] Starting an alignment process using the corresponding manipulator"),
                                        WireMock.containing("[INFO] Pushing aligned changes"),
                                        AdjustTestUtils.getWireMockContainingPredicate(
                                                ProcessStageUtils.Step.END,
                                                AdjustProcessStage.ALIGNMENT))));
        wireMock.verifyThat(
                1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(CALLBACK_PATH))
                        .withHeader(CONTENT_TYPE_STRING, WireMock.equalTo(MediaType.APPLICATION_JSON))
                        .withRequestBody(
                                WireMock.equalToJson(
                                        objectMapper.writeValueAsString(
                                                AdjustResponse.builder()
                                                        .tag("reqour-eee")
                                                        .downstreamCommit("123")
                                                        .internalUrl(
                                                                InternalGitRepositoryUrl.builder()
                                                                        .readonlyUrl(
                                                                                "https://gitlab.com/test-workspace/repo/project.git")
                                                                        .readwriteUrl(
                                                                                "git@gitlab.com:test-workspace/repo/project.git")
                                                                        .build())
                                                        .upstreamCommit("abc")
                                                        .isRefRevisionInternal(true)
                                                        .manipulatorResult(
                                                                ManipulatorResult.builder()
                                                                        .versioningState(
                                                                                VersioningState.builder()
                                                                                        .executionRootName(
                                                                                                "com.example:foo")
                                                                                        .executionRootVersion("1.0.0")
                                                                                        .build())
                                                                        .removedRepositories(Collections.emptyList())
                                                                        .build())
                                                        .callback(
                                                                ReqourCallback.builder()
                                                                        .id("task123")
                                                                        .status(ResultStatus.SUCCESS)
                                                                        .build())
                                                        .build()))));
    }
}