/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.wildfly.common.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import jakarta.inject.Inject;

import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.utils.CommonUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AdjustProviderPickerTest {

    @Inject
    AdjustProviderPicker adjustProviderPicker;

    @BeforeAll
    static void beforeAll() {
        CommonUtils.createAdjustDirectory();
    }

    @AfterAll
    static void afterAll() throws IOException {
        Files.deleteIfExists(CommonUtils.getAdjustDir());
    }

    @Test
    void pickAdjustProvider_onMvnBuild_picksMvnProvider() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildConfigParameters(Collections.emptyMap())
                .buildType(BuildType.MVN)
                .build();

        AdjustProvider adjustProvider = adjustProviderPicker.pickAdjustProvider(adjustRequest);

        assertTrue(adjustProvider instanceof MvnProvider);
    }

    @Test
    void pickAdjustProvider_onRpmBuild_picksMvnProvider() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildConfigParameters(Collections.emptyMap())
                .buildType(BuildType.MVN_RPM)
                .build();

        AdjustProvider adjustProvider = adjustProviderPicker.pickAdjustProvider(adjustRequest);

        assertTrue(adjustProvider instanceof MvnProvider);
    }
}