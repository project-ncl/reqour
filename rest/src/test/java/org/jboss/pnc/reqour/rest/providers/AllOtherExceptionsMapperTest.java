/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.providers;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.Response;

import org.jboss.pnc.api.dto.ErrorResponse;
import org.junit.jupiter.api.Test;

class AllOtherExceptionsMapperTest {

    @Test
    void theExceptionMessageIsNotReturned() {
        Response response = new AllOtherExceptionsMapper()
                .toResponse(new IllegalArgumentException("invalid header value: \"secret-token\n\""));

        assertThat(response.getStatus()).isEqualTo(500);
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertThat(error.getErrorType()).isEqualTo("IllegalArgumentException");
        assertThat(error.getErrorMessage()).isEqualTo(AllOtherExceptionsMapper.MESSAGE).doesNotContain("secret");
    }
}
