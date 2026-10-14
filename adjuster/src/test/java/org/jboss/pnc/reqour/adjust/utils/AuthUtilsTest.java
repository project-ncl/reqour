/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class AuthUtilsTest {

    @Test
    void createBasicAuthRestHeaders_simpleCredentials() {
        String result = AuthUtils.createBasicAuthRestHeaders("testuser", "testpassword");

        assertThat(result).startsWith("Authorization:Basic ");

        String encoded = result.substring("Authorization:Basic ".length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("testuser:testpassword");
    }

    @Test
    void createBasicAuthRestHeaders_specialCharactersInPassword() {
        String result = AuthUtils.createBasicAuthRestHeaders("svc-account", "p@ss:word!#$");

        String encoded = result.substring("Authorization:Basic ".length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("svc-account:p@ss:word!#$");
    }

    @Test
    void createBasicAuthRestHeaders_producesValidBase64WithPadding() {
        // "a:bb" is 4 bytes -> Base64 produces 8 chars with padding ("YTpiYg==")
        String result = AuthUtils.createBasicAuthRestHeaders("a", "bb");

        String encoded = result.substring("Authorization:Basic ".length());
        assertThat(encoded).isEqualTo("YTpiYg==");
    }
}