/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.providers;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jboss.pnc.api.dto.ErrorResponse;
import org.junit.jupiter.api.Test;

class WebApplicationExceptionMapperTest {

    @Test
    void serverErrorMessagesAreNotReturned() {
        Response response = new WebApplicationExceptionMapper().toResponse(
                new WebApplicationException("call to https://user:secret@host failed", Response.Status.BAD_GATEWAY));

        assertThat(response.getStatus()).isEqualTo(502);
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertThat(error.getErrorType()).isEqualTo("WebApplicationException");
        assertThat(error.getErrorMessage()).isEqualTo(AllOtherExceptionsMapper.MESSAGE).doesNotContain("secret");
    }

    @Test
    void otherAnswersAreReturnedAsThrown() {
        Response accepted = Response.accepted().build();

        assertThat(new WebApplicationExceptionMapper().toResponse(new WebApplicationException(accepted)))
                .isSameAs(accepted);
    }
}
