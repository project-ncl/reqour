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
package org.jboss.pnc.reqour.rest.providers;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.dto.ErrorResponse;
import org.jboss.pnc.reqour.common.exceptions.UnsupportedCloneTypeException;

@Provider
@Slf4j
public class UnsupportedCloneTypeExceptionMapper implements ExceptionMapper<UnsupportedCloneTypeException> {

    @Override
    public Response toResponse(UnsupportedCloneTypeException exception) {
        log.warn("Unsupported clone type provided: ", exception);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(exception))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
}
