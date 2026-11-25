/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.jboss.pnc.reqour.common.utils.HttpHeaderValues;
import org.jboss.pnc.reqour.config.validation.GitProviderTokensCheck;
import org.junit.jupiter.api.Test;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class GitProviderTokenConfigTest {

    @Test
    void theTokenIsStrippedAtTheConfigBoundary() {
        assertThat(gitlab("token\n").token()).isEqualTo("token");
        assertThat(gitlab("  token \r\n").token()).isEqualTo("token");
    }

    @Test
    void invalidTokensAreReportedAtStartupWithoutFailingIt() {
        assertThat(GitProviderTokensCheck.check("gitlab", gitlab("token"))).isTrue();
        assertThat(GitProviderTokensCheck.check("gitlab", gitlab("to\nken"))).isFalse();
    }

    @Test
    void headerValues() {
        assertThat(HttpHeaderValues.valid("Bearer glpat-abc.def_123")).isTrue();
        assertThat(HttpHeaderValues.valid("with inner space")).isTrue();
        assertThat(HttpHeaderValues.valid("line\nbreak")).isFalse();
        assertThat(HttpHeaderValues.valid("carriage\rreturn")).isFalse();
    }

    private static GitLabProviderConfig gitlab(String token) {
        Map<String, String> values = new HashMap<>();
        values.put("p.enabled", "true");
        values.put("p.url", "https://gitlab.example.com");
        values.put("p.username", "bot");
        values.put("p.hostname", "gitlab.example.com");
        values.put("p.read-only-template", "ro");
        values.put("p.read-write-template", "rw");
        values.put("p.git-url-internal-template", "git");
        values.put("p.workspace", "workspace");
        values.put("p.workspace-id", "1");
        values.put("p.token", token);
        SmallRyeConfig config = new SmallRyeConfigBuilder().withMapping(GitLabProviderConfig.class, "p")
                .withDefaultValues(values)
                .build();
        return config.getConfigMapping(GitLabProviderConfig.class, "p");
    }
}
