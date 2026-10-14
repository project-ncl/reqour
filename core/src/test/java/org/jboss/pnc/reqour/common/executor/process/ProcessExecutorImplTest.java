/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.executor.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class ProcessExecutorImplTest {

    @Test
    void logProcessContext_commandOnly() {
        assertThat(ProcessExecutorImpl.logProcessContext(List.of("git", "clone"), Path.of("/foo/bar"), null))
                .isEqualTo("cd /foo/bar && git clone");

        assertThat(ProcessExecutorImpl.logProcessContext(List.of("git", "clo ne"), Path.of("/fo o/bar"), null))
                .isEqualTo("cd \"/fo o/bar\" && git \"clo ne\"");
    }

    @Test
    void logProcessContext_commandOnly_doesNotContainEnvVars() {
        // Passing null env vars produces a command-only string with no KEY=value pairs
        String result = ProcessExecutorImpl.logProcessContext(
                List.of("java", "-jar", "app.jar"),
                Path.of("/tmp/work"),
                null);
        assertThat(result).doesNotContain("ARTIFACTORY_READ_TOKEN");
        assertThat(result).doesNotContain("ARTIFACTORY_URL");
    }

    @Test
    void logProcessContext() {
        assertThat(ProcessExecutorImpl.logProcessContext(List.of("git", "clone"), Path.of("/foo/bar"), Map.of()))
                .isEqualTo("cd /foo/bar && git clone");

        assertThat(
                ProcessExecutorImpl
                        .logProcessContext(List.of("git", "clone"), Path.of("/foo/bar"), Map.of("FOO", "bar")))
                .isEqualTo("cd /foo/bar && FOO=bar git clone");

        assertThat(
                ProcessExecutorImpl
                        .logProcessContext(List.of("git", "clo ne"), Path.of("/fo o/bar"), Map.of("FOO", "bar baz")))
                .isEqualTo("cd \"/fo o/bar\" && FOO=\"bar baz\" git \"clo ne\"");
    }

    @Test
    void logProcessContext_envVarsArePresentForDebug() {
        // Short placeholder value — this test only checks the key=value format, not masking
        String result = ProcessExecutorImpl.logProcessContext(
                List.of("java", "-jar", "app.jar"),
                Path.of("/tmp/work"),
                Map.of("ARTIFACTORY_READ_TOKEN", "test-token-value"));
        assertThat(result).contains("ARTIFACTORY_READ_TOKEN=test-token-value");
    }

    @Test
    void maskKnownSecrets_masksBasicCredentialInRestHeaders() {
        // Basic token must be ≥20 chars to be matched by LogSanitizer's BASIC_CREDENTIALS pattern.
        // Randomly generated — not a real credential.
        String token = "ZZiUV8G4ehK6hcQJnGmlXkvp8TzE"; // HashiCorpIgnore
        String command = "java -jar app.jar -DrestHeaders=Authorization:Basic " + token;
        String masked = org.jboss.pnc.common.log.LogSanitizer.maskKnownSecrets(command);
        assertThat(masked).doesNotContain(token);
        assertThat(masked).contains("[REDACTED]");
    }

    @Test
    void maskKnownSecrets_masksArtifactoryToken() {
        // Randomly generated — not a real credential.
        String token = "z5VHqFJ7V06eWJIqyLNAJ4kVNAyL6I41YbGtm24xDWiGcLu1qPBEmDu8"; // HashiCorpIgnore
        String envString = "{ARTIFACTORY_READ_TOKEN=" + token + ", ARTIFACTORY_URL=https://artifactory.example.com}";
        String masked = org.jboss.pnc.common.log.LogSanitizer.maskKnownSecrets(envString);
        assertThat(masked).doesNotContain(token);
        assertThat(masked).contains("[REDACTED]");
    }
}
