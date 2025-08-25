/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.manipulator.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.model.LocationAndRemainingAlignmentParameters;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.junit.jupiter.api.Test;

class CommonManipulatorConfigUtilsTest {

    private static final String FILE_SHORT_OPTION_NAME = "f";
    private static final String FILE_LONG_OPTION_NAME = "file";

    @Test
    void parseUserSpecifiedAlignmentParameters_noLocation_returnsParsedAlignmentParametersWithDefaultLocation() {
        AdjustRequest request = AdjustRequest.builder()
                .buildConfigParameters(
                        Map.of(BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS, "-Dfoo=bar -Dbaz=\"baz baz\""))
                .build();
        UserSpecifiedAlignmentParameters expected = UserSpecifiedAlignmentParameters.builder()
                .locationOption(UserSpecifiedAlignmentParameters.getDefaultLocationOption())
                .alignmentParameters(List.of("-Dfoo=bar", "-Dbaz=baz baz"))
                .build();

        UserSpecifiedAlignmentParameters actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(request);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void parseUserSpecifiedAlignmentParameters_withLocation_returnsParsedAlignmentParametersWithLocation() {
        AdjustRequest request = AdjustRequest.builder()
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-Dfoo=bar --file=/tmp/dir/file -Dbaz=\"baz baz\""))
                .build();
        UserSpecifiedAlignmentParameters expected = UserSpecifiedAlignmentParameters.builder()
                .locationOption(Optional.of(Path.of("/tmp/dir/file")))
                .alignmentParameters(List.of("-Dfoo=bar", "-Dbaz=baz baz"))
                .build();

        UserSpecifiedAlignmentParameters actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(request);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_noLocation_returnsUnchangedString() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar  -Dbaz=baz";
        LocationAndRemainingAlignmentParameters expected = LocationAndRemainingAlignmentParameters.builder()
                .locationOption(Optional.empty())
                .remainingAlignmentParameters(userSpecifiedAlignmentParams)
                .build();

        LocationAndRemainingAlignmentParameters actual = CommonManipulatorConfigUtils
                .extractLocationFromUsersAlignmentParameters(
                        userSpecifiedAlignmentParams,
                        FILE_SHORT_OPTION_NAME,
                        FILE_LONG_OPTION_NAME);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_withShortLocationOption_returnsParametersWithoutLocation() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar -f dir/pom.xml -Dbaz=baz";
        LocationAndRemainingAlignmentParameters expected = LocationAndRemainingAlignmentParameters.builder()
                .locationOption(Optional.of("-f dir/pom.xml"))
                .remainingAlignmentParameters("-Dfoo=bar  -Dbaz=baz")
                .build();

        LocationAndRemainingAlignmentParameters actual = CommonManipulatorConfigUtils
                .extractLocationFromUsersAlignmentParameters(
                        userSpecifiedAlignmentParams,
                        FILE_SHORT_OPTION_NAME,
                        FILE_LONG_OPTION_NAME);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_withLongLocationOption_returnsParametersWithoutLocation() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar --file=dir/subdir/pom.tpl.xml -Dbaz=baz";
        LocationAndRemainingAlignmentParameters expected = LocationAndRemainingAlignmentParameters.builder()
                .locationOption(Optional.of("--file=dir/subdir/pom.tpl.xml"))
                .remainingAlignmentParameters("-Dfoo=bar  -Dbaz=baz")
                .build();

        LocationAndRemainingAlignmentParameters actual = CommonManipulatorConfigUtils
                .extractLocationFromUsersAlignmentParameters(
                        userSpecifiedAlignmentParams,
                        FILE_SHORT_OPTION_NAME,
                        FILE_LONG_OPTION_NAME);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_customLocationOptionWithLongLocationOption_returnsParametersWithoutLocation() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar --target=dir/subdir -Dbaz=baz";
        LocationAndRemainingAlignmentParameters expected = LocationAndRemainingAlignmentParameters.builder()
                .locationOption(Optional.of("--target=dir/subdir"))
                .remainingAlignmentParameters("-Dfoo=bar  -Dbaz=baz")
                .build();

        LocationAndRemainingAlignmentParameters actual = CommonManipulatorConfigUtils
                .extractLocationFromUsersAlignmentParameters(
                        userSpecifiedAlignmentParams,
                        "t",
                        "target");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void getJavaLocation_noJavaVersionOverride_defaultIsUsed() {
        assertThat(CommonManipulatorConfigUtils.getJavaLocation(Collections.emptyList())).isEqualTo(
                CommonManipulatorConfigUtils.getJavaOfVersion(CommonManipulatorConfigUtils.DEFAULT_JAVA_VERSION));
    }

    @Test
    void getJavaLocation_java17VersionOverrideGiven_overrideIsUsed() {
        assertThat(CommonManipulatorConfigUtils.getJavaLocation(List.of("-DRepour_Java=17")))
                .isEqualTo(CommonManipulatorConfigUtils.getJavaOfVersion("17"));
    }

    @Test
    void getJavaLocation_java8VersionOverrideGiven_overrideIsUsed() {
        assertThat(CommonManipulatorConfigUtils.getJavaLocation(List.of("-DRepour_Java=1.8.0")))
                .isEqualTo(CommonManipulatorConfigUtils.getJavaOfVersion("1.8.0"));
    }

    @Test
    void getJavaLocation_invalidJavaVersionOverrideGiven_throwsException() {
        assertThatThrownBy(() -> CommonManipulatorConfigUtils.getJavaLocation(List.of("-DRepour_Java=invalid")))
                .isInstanceOf(AdjusterException.class)
                .hasMessage("Invalid Java version 'invalid' provided.");
    }
}