/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jboss.pnc.api.dto.GA;
import org.jboss.pnc.api.dto.GAV;
import org.jboss.pnc.reqour.common.profile.AdjustProfile;
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
        GAV expectedGav = GAV.builder()
                .ga(GA.builder().groupId("com.example").artifactId("foo").build())
                .version("1.0.0.rh-42")
                .build();

        GAV gav = rootGavExtractor.extractGav(workdir);

        assertThat(gav).isEqualTo(expectedGav);
    }

    @Test
    void extractGav_withHierarchy_extractsCorrectly() {
        Path workdir = GAV_EXTRACTOR_TEST_DIR.resolve("with-hierarchy/child");
        GAV expectedGav = GAV.builder()
                .ga(GA.builder().groupId("com.example").artifactId("child").build())
                .version("1.0.0.rh-314")
                .build();

        GAV gav = rootGavExtractor.extractGav(workdir);

        assertThat(gav).isEqualTo(expectedGav);
    }
}