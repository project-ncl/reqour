/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.common.profile.UtilsProfile;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.TestProfile;

@TestProfile(UtilsProfile.class)
class IOUtilsTest {

    @Test
    void countLinesWithTrailingNewline() {
        assertThat(IOUtils.countLines("line 1" + System.lineSeparator() + "line 2" + System.lineSeparator()))
                .isEqualTo(2);
    }

    @Test
    void countLinesWithoutTrailingNewline() {
        assertThat(IOUtils.countLines("line 1" + System.lineSeparator() + "line 2")).isEqualTo(2);
    }

    @Test
    void splitByNewLineWithTrailingNewline() {
        String one = "one";
        String two = "two";
        String text = one + System.lineSeparator() + two + System.lineSeparator();

        assertThat(IOUtils.splitByNewLine(text)).isEqualTo(List.of(one, two));
    }

    @Test
    void splitByNewLineWithoutTrailingNewline() {
        String one = "one";
        String two = "two";
        String text = one + System.lineSeparator() + two;

        assertThat(IOUtils.splitByNewLine(text)).isEqualTo(List.of(one, two));
    }

    @Test
    void unquote_noQuotes_returnsOriginalString() {
        String text = "foo";

        String actual = IOUtils.unquote(text);

        assertThat(actual).isEqualTo(text);
    }

    @Test
    void unquote_withQuotes_returnsUnquotedString() {
        String text = "foo bar";

        String actual = IOUtils.unquote("\"" + text + "\"");

        assertThat(actual).isEqualTo(text);
    }

    @Test
    void unescapeUserAlignmentParameters_noBuildConfigParameters_returnsUnchanged() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .build();

        assertThat(IOUtils.unescapeUserAlignmentParameters(adjustRequest)).isEqualTo(adjustRequest);
    }

    @Test
    void unescapeUserAlignmentParameters_noUserAlignmentParameters_returnsUnchanged() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildConfigParameters(Collections.emptyMap())
                .build();

        assertThat(IOUtils.unescapeUserAlignmentParameters(adjustRequest)).isEqualTo(adjustRequest);
    }

    @Test
    void unescapeUserAlignmentParameters_userAlignmentParametersWithoutDollars_returnsUnchanged() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildType(BuildType.MVN)
                .buildConfigParameters(Map.of(BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS, "-Dfoo=bar"))
                .build();

        assertThat(IOUtils.unescapeUserAlignmentParameters(adjustRequest)).isEqualTo(adjustRequest);
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

        assertThat(IOUtils.unescapeUserAlignmentParameters(adjustRequest)).isEqualTo(unescapedRequest);
    }
}