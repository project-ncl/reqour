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
package org.jboss.pnc.reqour.api;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.pnc.api.dto.ErrorResponse;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.reqour.api.openapi.OpenapiConstants;

@Tag(name = "Translation")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/git-external-to-internal")
public interface TranslationEndpoint {

    String EXTERNAL_TO_INTERNAL_DESC = "Translate external git repo URL into internal";

    @Operation(summary = EXTERNAL_TO_INTERNAL_DESC)
    @APIResponses({
            @APIResponse(
                    responseCode = OpenapiConstants.SUCCESS_CODE,
                    description = OpenapiConstants.SUCCESS_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = TranslateResponse.class))),
            @APIResponse(
                    responseCode = OpenapiConstants.BAD_REQUEST_CODE,
                    description = OpenapiConstants.BAD_REQUEST_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(
                    responseCode = OpenapiConstants.SERVER_ERROR_CODE,
                    description = OpenapiConstants.SERVER_ERROR_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))) })
    @POST
    TranslateResponse externalToInternal(@Valid TranslateRequest externalToInternalRequestDto);
}
