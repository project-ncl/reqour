/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.assertj.core.data.MapEntry;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.adjust.profiles.MvnAdjustProfile;
import org.jboss.pnc.reqour.adjust.profiles.NpmAdjustProfile;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.adjust.provider.TestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.provider.TestUtils.assertSystemPropertyHasValuesSortedByPriority;

@QuarkusTest
@TestProfile(NpmAdjustProfile.class)
class NpmProviderTest {

    @Inject
    ReqourAdjusterConfig config;

    @Inject
    TestUtils testUtils;

    static Path workdir;

    @BeforeAll
    static void beforeAll() {
        workdir = IOUtils.createTempDirForAdjust();
    }

    @AfterAll
    static void afterAll() throws IOException {
        IOUtils.deleteTempDir(workdir);
    }

    @Test
    void prepareCommand_standardTemporaryBuildWithPersistentPreference_generatedCommandIsCorrect() {
        NpmProvider provider = new NpmProvider(
                config.adjust(),
                testUtils.getAdjustRequest(Path.of("npm-request.json")),
                workdir);

        List<String> command = provider.preparedCommand;

        assertThat(command)
                .containsSequence(List.of("java", "-jar", config.adjust().npmProviderConfig().cliJarPath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 3)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("TEMPORARY_PREFER_PERSISTENT"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of("default", "user", "temporary-user"));
    }
}