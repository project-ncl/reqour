/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2024-2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
