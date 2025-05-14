/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.common.TestDataSupplier.BIFROST_FINAL_LOG_UPLOAD_PATH;
import static org.jboss.pnc.reqour.common.TestDataSupplier.CALLBACK_PATH;
import static org.jboss.pnc.reqour.common.TestDataSupplier.TASK_ID;
import static org.jboss.pnc.reqour.rest.endpoints.AdjustEndpointImpl.getMessageStepStartingAlignmentPod;
import static org.jboss.pnc.reqour.rest.endpoints.TestConstants.TEST_USER;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.pnc.api.constants.MDCHeaderKeys;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.AdjustResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.rest.AdjustEndpoint;
import org.jboss.pnc.common.log.ProcessStageUtils;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.common.profile.AdjustProfile;
import org.jboss.pnc.reqour.rest.openshift.OpenShiftAdjusterJobController;
import org.jboss.pnc.reqour.rest.service.FinalLogManager;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;

@QuarkusTest
@TestProfile(AdjustProfile.class)
@TestHTTPEndpoint(AdjustEndpoint.class)
@ConnectWireMock
@TestSecurity(user = TEST_USER, roles = { OidcRoleConstants.PNC_APP_REPOUR_USER })
class AdjustEndpointImplTest {

    private static final String PROCESS_CONTEXT = "my-process-context";
    private static final String EXCEPTION_MESSAGE = "Ooops, something went terribly wrongie";

    WireMock wireMock;

    @InjectMock
    OpenShiftAdjusterJobController adjusterJobController;

    @InjectSpy
    FinalLogManager finalLogManager;

    @InjectMock
    OpenShiftAdjusterJobController openShiftAdjusterJobController;

    @UserLogger
    @InjectSpy
    Logger userLogger;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        wireMock.register(WireMock.post(BIFROST_FINAL_LOG_UPLOAD_PATH).willReturn(WireMock.ok()));
        wireMock.register(WireMock.post(CALLBACK_PATH).willReturn(WireMock.ok()));
    }

    @Test
    void adjust_adjusterJobCreationSuccessed_sendsFinalLogToBifrost() throws InterruptedException {
        Mockito.doNothing().when(adjusterJobController).createAdjusterJob(ArgumentMatchers.any());

        Response response = RestAssured.given()
                .contentType(MediaType.APPLICATION_JSON)
                .header(new Header(MDCHeaderKeys.PROCESS_CONTEXT.getHeaderName(), PROCESS_CONTEXT))
                .body(TestUtils.createAdjustRequest())
                .when()
                .post();

        assertThat(response.statusCode()).isEqualTo(jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode());
        Thread.sleep(2_000);
        wireMock.verifyThat(
                1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(BIFROST_FINAL_LOG_UPLOAD_PATH))
                        .withRequestBody(
                                WireMock.and(
                                        WireMock.containing(
                                                getMessageStepStartingAlignmentPod(
                                                        ProcessStageUtils.Step.BEGIN)),
                                        WireMock.containing(
                                                "Adjuster Job for taskID='" + TASK_ID
                                                        + "' was successfully requested to be created"))));
    }

    @Test
    void adjust_adjusterJobCreationFailed_sendsFinalLogToBifrost()
            throws InterruptedException, JsonProcessingException {
        RuntimeException ex = new RuntimeException(EXCEPTION_MESSAGE);
        Mockito.doThrow(ex)
                .when(adjusterJobController)
                .createAdjusterJob(ArgumentMatchers.any());

        Response response = RestAssured.given()
                .contentType(MediaType.APPLICATION_JSON)
                .header(new Header(MDCHeaderKeys.PROCESS_CONTEXT.getHeaderName(), PROCESS_CONTEXT))
                .body(TestUtils.createAdjustRequest())
                .when()
                .post();

        // exception thrown from another thread, so 202 Accepted status code is expected
        assertThat(response.statusCode()).isEqualTo(jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode());
        Thread.sleep(2_000);
        wireMock.verifyThat(
                1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(BIFROST_FINAL_LOG_UPLOAD_PATH))
                        .withRequestBody(
                                WireMock.and(
                                        WireMock.containing(
                                                getMessageStepStartingAlignmentPod(
                                                        ProcessStageUtils.Step.BEGIN)),
                                        WireMock.containing(
                                                getMessageStepStartingAlignmentPod(
                                                        ProcessStageUtils.Step.END)))));
        WireMockUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(
                        AdjustResponse.builder()
                                .callback(
                                        ReqourCallback.builder().id(TASK_ID).status(ResultStatus.SYSTEM_ERROR).build())
                                .build()));
        userLogger.error("Alignment pod creation ended with the exception: {}", EXCEPTION_MESSAGE, ex);
    }

    @Test
    void adjust_finalLogSendingFails_podIsDestroyedAndErrorLog()
            throws InterruptedException, JsonProcessingException {
        wireMock.resetRequests();
        Mockito.doNothing().when(adjusterJobController).createAdjusterJob(ArgumentMatchers.any());
        NullPointerException ex = new NullPointerException(EXCEPTION_MESSAGE);
        Mockito.doThrow(ex)
                .when(finalLogManager)
                .sendMessage();
        Mockito.when(openShiftAdjusterJobController.destroyAdjusterJob(TASK_ID)).thenReturn(ResultStatus.SUCCESS);

        Response response = RestAssured.given()
                .contentType(MediaType.APPLICATION_JSON)
                .header(new Header(MDCHeaderKeys.PROCESS_CONTEXT.getHeaderName(), PROCESS_CONTEXT))
                .body(TestUtils.createAdjustRequest())
                .when()
                .post();

        // exception thrown from another thread, so 202 Accepted status code is expected
        assertThat(response.statusCode()).isEqualTo(jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode());
        Thread.sleep(2_000);
        wireMock.verifyThat(
                0,
                WireMock.postRequestedFor(WireMock.urlEqualTo(BIFROST_FINAL_LOG_UPLOAD_PATH)));
        Mockito.verify(openShiftAdjusterJobController).destroyAdjusterJob(TASK_ID);
        WireMockUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(
                        AdjustResponse.builder()
                                .callback(
                                        ReqourCallback.builder().id(TASK_ID).status(ResultStatus.SYSTEM_ERROR).build())
                                .build()));
        Mockito.verify(userLogger).error("Could not send final log to Bifrost", ex);
    }
}
