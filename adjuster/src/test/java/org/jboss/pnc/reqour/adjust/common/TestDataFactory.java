/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.common;

import java.util.Map;

import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestDataFactory {

    public static Logger userLogger = LoggerFactory.getLogger("com.example.userlogger");

    public static final String STANDARD_BUILD_CATEGORY = "STANDARD";
    public static final String TEST_BUILD_CATEGORY = "TEST";

    public static AdjustRequest STANDARD_PERSISTENT_REQUEST = AdjustRequest.builder()
            .buildConfigParameters(Map.of(BuildConfigurationParameterKeys.BUILD_CATEGORY, STANDARD_BUILD_CATEGORY))
            .tempBuild(false)
            .brewPullActive(true)
            .build();

    public static AdjustRequest MANIPULATOR_DISABLED_REQUEST = AdjustRequest.builder()
            .buildConfigParameters(
                    Map.of(
                            BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                            "-D" + AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.MANIPULATION_DISABLE
                                    .getCliName()
                                    + "=true"))
            .tempBuild(false)
            .brewPullActive(true)
            .build();

    public static AdjustRequest STANDARD_TEMPORARY_REQUEST = AdjustRequest.builder()
            .buildConfigParameters(Map.of(BuildConfigurationParameterKeys.BUILD_CATEGORY, STANDARD_BUILD_CATEGORY))
            .tempBuild(true)
            .build();

    public static AdjustRequest TEST_PERSISTENT_REQUEST = AdjustRequest.builder()
            .buildConfigParameters(Map.of(BuildConfigurationParameterKeys.BUILD_CATEGORY, TEST_BUILD_CATEGORY))
            .tempBuild(false)
            .brewPullActive(true)
            .build();

    public static AdjustRequest TEST_TEMPORARY_REQUEST = AdjustRequest.builder()
            .buildConfigParameters(Map.of(BuildConfigurationParameterKeys.BUILD_CATEGORY, TEST_BUILD_CATEGORY))
            .tempBuild(true)
            .build();
}
