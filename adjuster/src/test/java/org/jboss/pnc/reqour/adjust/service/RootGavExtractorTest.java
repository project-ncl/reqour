/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jboss.pnc.api.dto.Gav;
import org.jboss.pnc.reqour.adjust.profiles.AdjustProfile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(AdjustProfile.class)
class RootGavExtractorTest {

    @Inject
    RootGavExtractor rootGavExtractor;

    private final Path GAV_EXTRACTOR_TEST_DIR = Path.of("src/test/resources/service/gav-extractor");

    @Test
    void extractGav_noHierarchy_extractsCorrectly() {
        Path workdir = GAV_EXTRACTOR_TEST_DIR.resolve("no-hierarchy");
        Gav expectedGav = Gav.builder().groupId("com.example").artifactId("foo").version("1.0.0.rh-42").build();

        Gav gav = rootGavExtractor.extractGav(workdir);

        assertThat(gav).isEqualTo(expectedGav);
    }

    @Test
    void extractGav_withHierarchy_extractsCorrectly() {
        Path workdir = GAV_EXTRACTOR_TEST_DIR.resolve("with-hierarchy/child");
        Gav expectedGav = Gav.builder().groupId("com.example").artifactId("child").version("1.0.0.rh-314").build();

        Gav gav = rootGavExtractor.extractGav(workdir);

        assertThat(gav).isEqualTo(expectedGav);
    }
}