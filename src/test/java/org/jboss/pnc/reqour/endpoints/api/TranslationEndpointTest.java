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
package org.jboss.pnc.reqour.endpoints.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class TranslationEndpointTest {

    @Test
    void testExternalToInternalEndpoint() {
        given().when()
                .contentType(ContentType.JSON)
                .body(TranslateResponse.builder().externalUrl("whatever").build())
                .post("/external-to-internal")
                .then()
                .statusCode(200)
                .body(
                        is(
                                "{\"externalUrl\":\"whatever\",\"internalUrl\":\"git@gitlab.cee.redhat.com:pnc-workspace/project-ncl/reqour.git\"}"));
    }
}