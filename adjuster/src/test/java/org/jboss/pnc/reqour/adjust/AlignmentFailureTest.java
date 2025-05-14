/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import static org.jboss.pnc.api.constants.HttpHeaders.CONTENT_TYPE_STRING;
import static org.jboss.pnc.reqour.common.TestDataSupplier.BIFROST_FINAL_LOG_UPLOAD_PATH;
import static org.jboss.pnc.reqour.common.TestDataSupplier.CALLBACK_PATH;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.AdjustResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.common.log.ProcessStageUtils;
import org.jboss.pnc.reqour.adjust.profile.WithFailingAdjustProviderAlternative;
import org.jboss.pnc.reqour.enums.AdjustProcessStage;
import org.junit.jupiter.api.AfterEach;
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
@TestProfile(WithFailingAdjustProviderAlternative.class)
@ConnectWireMock
public class AlignmentFailureTest {

    @Inject
    @TopCommand
    App app;

    WireMock wireMock;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AdjustTestUtils adjustTestUtils;

    @BeforeEach
    void setUp() {
        wireMock.register(WireMock.post(BIFROST_FINAL_LOG_UPLOAD_PATH).willReturn(WireMock.ok()));
        wireMock.register(WireMock.post(CALLBACK_PATH).willReturn(WireMock.ok()));
        wireMock.register(WireMock.post(adjustTestUtils.getHeartbeatPath()).willReturn(WireMock.ok()));
    }

    @AfterEach
    void tearDown() {
        wireMock.resetRequests();
    }

    @Test
    void run_alignmentFailure_finalLogAndCallbackSent() throws InterruptedException, JsonProcessingException {
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
                                                "[WARN] Exception was: org.jboss.pnc.reqour.adjust.exception.AdjusterException: Oops, alignment exception"),
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
                                                        .callback(
                                                                ReqourCallback.builder()
                                                                        .id("task123")
                                                                        .status(ResultStatus.FAILED)
                                                                        .build())
                                                        .build()))));
    }
}
