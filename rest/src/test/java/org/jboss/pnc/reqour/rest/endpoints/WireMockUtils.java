/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.ws.rs.core.MediaType;

import static org.jboss.pnc.api.constants.HttpHeaders.CONTENT_TYPE_STRING;

public class WireMockUtils {

    public static void registerGet(WireMock wireMock, String url, String expectedJson) {
        wireMock.register(
                WireMock.get(url)
                        .willReturn(
                                WireMock.ok(expectedJson).withHeader(CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON)));
    }

    public static void verifyThatCallbackWasSent(WireMock wireMock, String callbackPath, String expectedBody) {
        wireMock.verifyThat(
                1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(callbackPath))
                        .withHeader(CONTENT_TYPE_STRING, WireMock.equalTo(MediaType.APPLICATION_JSON))
                        .withRequestBody(WireMock.equalToJson(expectedBody)));
    }
}
