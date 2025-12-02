/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import static org.jboss.pnc.reqour.common.TestDataSupplier.CALLBACK_PATH;
import static org.jboss.pnc.reqour.rest.endpoints.TestConstants.TEST_USER;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.CancelResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.rest.CancelEndpoint;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;

import io.fabric8.kubernetes.api.model.StatusDetailsBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesServer;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;

@QuarkusTest
@TestHTTPEndpoint(CancelEndpoint.class)
@WithKubernetesTestServer(crud = false)
@ConnectWireMock
@TestSecurity(user = TEST_USER, roles = { OidcRoleConstants.PNC_APP_REPOUR_USER })
public class CancelEndpointTest {

    WireMock wireMock;

    @Inject
    ObjectMapper objectMapper;

    @KubernetesTestServer
    KubernetesServer mockedKubernetesServer;

    @BeforeEach
    void setUp() {
        String adjusterJobName = "reqour-adjuster-taskxid";
        String path = "/apis/batch/v1/namespaces/test/jobs/" + adjusterJobName;
        mockedKubernetesServer.expect()
                .get()
                .withPath(path)
                .andReturn(
                        200,
                        new JobBuilder().withKind("Job")
                                .withNewMetadata()
                                .withName(adjusterJobName)
                                .endMetadata()
                                .build())
                .always();
        mockedKubernetesServer.expect()
                .delete()
                .withPath(path)
                .andReturn(204, new ArrayList<>(List.of(new StatusDetailsBuilder().withName(adjusterJobName).build())))
                .always();

        wireMock.register(WireMock.post(CALLBACK_PATH).willReturn(WireMock.ok()));
    }

    @Test
    void cancel_adjusterJobNotExists_sendsCallbackWithFailedStatus()
            throws InterruptedException, JsonProcessingException {
        String taskId = "task-id-with-non-existent-adjuster";
        RestAssured.given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(TestUtils.createCancelRequest(taskId, CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(2_000);
        WireMockUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(
                        CancelResponse.builder()
                                .callback(ReqourCallback.builder().id(taskId).status(ResultStatus.FAILED).build())
                                .build()));
    }

    @Test
    void cancel_adjusterJobExists_sendsCallbackWithCancelledStatus()
            throws InterruptedException, JsonProcessingException {
        RestAssured.given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(TestUtils.createCancelRequest(TestDataSupplier.TASK_ID, CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(2_000);
        WireMockUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(
                        CancelResponse.builder()
                                .callback(
                                        ReqourCallback.builder()
                                                .id(TestDataSupplier.TASK_ID)
                                                .status(ResultStatus.CANCELLED)
                                                .build())
                                .build()));
    }
}
