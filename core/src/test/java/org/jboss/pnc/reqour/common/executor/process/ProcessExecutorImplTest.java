/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.executor.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jboss.pnc.reqour.common.profile.CommonTestProfile;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.TestProfile;

@TestProfile(CommonTestProfile.class)
public class ProcessExecutorImplTest {

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

}
