/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.assertj.core.data.MapEntry;
import org.jboss.pnc.reqour.adjust.TestUtils;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.adjust.profiles.SbtAdjustProfile;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.adjust.TestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.TestUtils.assertSystemPropertyHasValuesSortedByPriority;

@QuarkusTest
@TestProfile(SbtAdjustProfile.class)
class SbtProviderTest {

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
    void prepareCommand_standardTemporaryBuildWithPersistentAlignmentPreference_generatedCommandIsCorrect() {
        SbtProvider provider = new SbtProvider(
                config.adjust(),
                testUtils.getAdjustRequest(Path.of("sbt-request.json")),
                workdir,
                null,
                null,
                null);

        List<String> command = provider.prepareCommand();

        assertThat(command).containsSequence(List.of(config.adjust().scalaProviderConfig().sbtPath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 1),
                        MapEntry.entry("restBrewPullActive", 1)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("TEMPORARY_PREFER_PERSISTENT"));
        assertSystemPropertyHasValuesSortedByPriority(command, "versionIncrementalSuffix", List.of("temporary-redhat"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("false"));
    }
}