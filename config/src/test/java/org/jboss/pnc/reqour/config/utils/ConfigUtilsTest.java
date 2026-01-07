/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.junit.jupiter.api.Test;

class ConfigUtilsTest {

    @Test
    void unescapeUserAlignmentParameters_noBuildConfigParameters_returnsUnchanged() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .build();

        assertThat(ConfigUtils.unescapeUserAlignmentParameters(adjustRequest)).isEqualTo(adjustRequest);
    }

    @Test
    void unescapeUserAlignmentParameters_noUserAlignmentParameters_returnsUnchanged() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildConfigParameters(Collections.emptyMap())
                .build();

        assertThat(ConfigUtils.unescapeUserAlignmentParameters(adjustRequest)).isEqualTo(adjustRequest);
    }

    @Test
    void unescapeUserAlignmentParameters_userAlignmentParametersWithoutDollars_returnsUnchanged() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildType(BuildType.MVN)
                .buildConfigParameters(Map.of(BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS, "-Dfoo=bar"))
                .build();

        assertThat(ConfigUtils.unescapeUserAlignmentParameters(adjustRequest)).isEqualTo(adjustRequest);
    }

    @Test
    void unescapeUserAlignmentParameters_userAlignmentParametersWithDollars_returnsUnescaped() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildType(BuildType.MVN)
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-Dfoo=bar '-DaddDependency.io.netty:netty-transport-native-epoll:\\${netty.version}:compile:linux-x86_64@flink-shaded-netty-4'"))
                .build();
        AdjustRequest unescapedRequest = AdjustRequest.builder()
                .buildType(BuildType.MVN)
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-Dfoo=bar '-DaddDependency.io.netty:netty-transport-native-epoll:${netty.version}:compile:linux-x86_64@flink-shaded-netty-4'"))
                .build();

        assertThat(ConfigUtils.unescapeUserAlignmentParameters(adjustRequest)).isEqualTo(unescapedRequest);
    }
}