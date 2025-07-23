/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.manipulator.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.model.LocationAndRemainingArgsOptions;
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
                .subFolderWithResults(UserSpecifiedAlignmentParameters.getDefaultSubFolder())
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
                .subFolderWithResults(Path.of("/tmp/dir"))
                .alignmentParameters(List.of("-Dfoo=bar", "-Dbaz=baz baz"))
                .build();

        UserSpecifiedAlignmentParameters actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(request);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_noLocation_returnsUnchangedString() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar  -Dbaz=baz";
        LocationAndRemainingArgsOptions expected = LocationAndRemainingArgsOptions.builder()
                .locationOption(Optional.empty())
                .remainingArgs(userSpecifiedAlignmentParams)
                .build();

        LocationAndRemainingArgsOptions actual = CommonManipulatorConfigUtils
                .extractLocationFromUsersAlignmentParameters(
                        userSpecifiedAlignmentParams,
                        FILE_SHORT_OPTION_NAME,
                        FILE_LONG_OPTION_NAME);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_withShortLocationOption_returnsParametersWithoutLocation() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar -f /tmp/location -Dbaz=baz";
        LocationAndRemainingArgsOptions expected = LocationAndRemainingArgsOptions.builder()
                .locationOption(Optional.of("-f /tmp/location"))
                .remainingArgs("-Dfoo=bar  -Dbaz=baz")
                .build();

        LocationAndRemainingArgsOptions actual = CommonManipulatorConfigUtils
                .extractLocationFromUsersAlignmentParameters(
                        userSpecifiedAlignmentParams,
                        FILE_SHORT_OPTION_NAME,
                        FILE_LONG_OPTION_NAME);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_withLongLocationOption_returnsParametersWithoutLocation() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar --file=/tmp/location -Dbaz=baz";
        LocationAndRemainingArgsOptions expected = LocationAndRemainingArgsOptions.builder()
                .locationOption(Optional.of("--file=/tmp/location"))
                .remainingArgs("-Dfoo=bar  -Dbaz=baz")
                .build();

        LocationAndRemainingArgsOptions actual = CommonManipulatorConfigUtils
                .extractLocationFromUsersAlignmentParameters(
                        userSpecifiedAlignmentParams,
                        FILE_SHORT_OPTION_NAME,
                        FILE_LONG_OPTION_NAME);

        assertThat(actual).isEqualTo(expected);
    }
}