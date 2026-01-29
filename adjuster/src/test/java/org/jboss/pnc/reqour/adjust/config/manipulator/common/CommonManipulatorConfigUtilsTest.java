/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.manipulator.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.LogRecord;

import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.model.LocationAndRemainingAlignmentParameters;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.junit.jupiter.api.Test;

import io.quarkus.test.LogCollectingTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import lombok.extern.slf4j.Slf4j;

@QuarkusTest
@QuarkusTestResource(
        value = LogCollectingTestResource.class,
        restrictToAnnotatedClass = true,
        initArgs = @ResourceArg(name = LogCollectingTestResource.LEVEL, value = "FINE"))
@Slf4j
class CommonManipulatorConfigUtilsTest {

    @Test
    void parseUserSpecifiedAlignmentParameters_noLocation_returnsParsedAlignmentParametersWithDefaultLocation() {
        AdjustRequest request = AdjustRequest.builder()
                .buildConfigParameters(
                        Map.of(BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS, "-Dfoo=bar -Dbaz=\"baz baz\""))
                .build();
        UserSpecifiedAlignmentParameters expected = UserSpecifiedAlignmentParameters.builder()
                .location(UserSpecifiedAlignmentParameters.getDefaultLocation())
                .alignmentParameters(List.of("-Dfoo=bar", "-Dbaz=baz baz"))
                .build();

        UserSpecifiedAlignmentParameters actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(request, "f", "file");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void parseUserSpecifiedAlignmentParameters_withLongLocationOptionSeparatedByEquals_returnsParsedAlignmentParametersWithLocation() {
        AdjustRequest request = AdjustRequest.builder()
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-Dfoo=bar --file=/tmp/dir/file -Dbaz=\"baz baz\""))
                .build();
        UserSpecifiedAlignmentParameters expected = UserSpecifiedAlignmentParameters.builder()
                .location(Optional.of(Path.of("/tmp/dir/file")))
                .alignmentParameters(List.of("-Dfoo=bar", "-Dbaz=baz baz"))
                .build();

        UserSpecifiedAlignmentParameters actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(request, "f", "file");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void parseUserSpecifiedAlignmentParameters_withLongLocationOptionSeparatedBySpace_returnsParsedAlignmentParametersWithLocation() {
        AdjustRequest request = AdjustRequest.builder()
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-Dfoo=bar --file /tmp/dir/file -Dbaz=\"baz baz\""))
                .build();
        UserSpecifiedAlignmentParameters expected = UserSpecifiedAlignmentParameters.builder()
                .location(Optional.of(Path.of("/tmp/dir/file")))
                .alignmentParameters(List.of("-Dfoo=bar", "-Dbaz=baz baz"))
                .build();

        UserSpecifiedAlignmentParameters actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(request, "f", "file");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void parseUserSpecifiedAlignmentParameters_withShortLocationOptionSeparatedByEquals_returnsParsedAlignmentParametersWithLocation() {
        AdjustRequest request = AdjustRequest.builder()
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-Dfoo=bar -f=/tmp/dir/file -Dbaz=\"baz baz\""))
                .build();
        UserSpecifiedAlignmentParameters expected = UserSpecifiedAlignmentParameters.builder()
                .location(Optional.of(Path.of("/tmp/dir/file")))
                .alignmentParameters(List.of("-Dfoo=bar", "-Dbaz=baz baz"))
                .build();

        UserSpecifiedAlignmentParameters actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(request, "f", "file");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void parseUserSpecifiedAlignmentParameters_withShortLocationOptionSeparatedBySpace_returnsParsedAlignmentParametersWithLocation() {
        AdjustRequest request = AdjustRequest.builder()
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-Dfoo=bar -f /tmp/dir/file -Dbaz=\"baz baz\""))
                .build();
        UserSpecifiedAlignmentParameters expected = UserSpecifiedAlignmentParameters.builder()
                .location(Optional.of(Path.of("/tmp/dir/file")))
                .alignmentParameters(List.of("-Dfoo=bar", "-Dbaz=baz baz"))
                .build();

        UserSpecifiedAlignmentParameters actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(request, "f", "file");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_noLocation_returnsUnchangedString() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar  -Dbaz=baz";
        LocationAndRemainingAlignmentParameters expected = LocationAndRemainingAlignmentParameters.builder()
                .location(Optional.empty())
                .remainingAlignmentParameters(userSpecifiedAlignmentParams)
                .build();

        LocationAndRemainingAlignmentParameters actual = CommonManipulatorConfigUtils
                .extractLocationFromUsersAlignmentParameters(
                        userSpecifiedAlignmentParams,
                        "f",
                        "file");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_withShortLocationOption_returnsParametersWithoutLocation() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar -f dir/pom.xml -Dbaz=baz";
        LocationAndRemainingAlignmentParameters expected = LocationAndRemainingAlignmentParameters.builder()
                .location(Optional.of(Path.of("dir/pom.xml")))
                .remainingAlignmentParameters("-Dfoo=bar  -Dbaz=baz")
                .build();

        LocationAndRemainingAlignmentParameters actual = CommonManipulatorConfigUtils
                .extractLocationFromUsersAlignmentParameters(
                        userSpecifiedAlignmentParams,
                        "f",
                        "file");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_withLongLocationOption_returnsParametersWithoutLocation() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar --file=dir/subdir/pom.tpl.xml -Dbaz=baz";
        LocationAndRemainingAlignmentParameters expected = LocationAndRemainingAlignmentParameters.builder()
                .location(Optional.of(Path.of("dir/subdir/pom.tpl.xml")))
                .remainingAlignmentParameters("-Dfoo=bar  -Dbaz=baz")
                .build();

        LocationAndRemainingAlignmentParameters actual = CommonManipulatorConfigUtils
                .extractLocationFromUsersAlignmentParameters(
                        userSpecifiedAlignmentParams,
                        "f",
                        "file");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void extractLocationFromUsersAlignmentParameters_customLocationOptionWithLongLocationOption_returnsParametersWithoutLocation() {
        String userSpecifiedAlignmentParams = "-Dfoo=bar --target=dir/subdir -Dbaz=baz";
        LocationAndRemainingAlignmentParameters expected = LocationAndRemainingAlignmentParameters.builder()
                .location(Optional.of(Path.of("dir/subdir")))
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
    void parseUserSpecifiedAlignmentParametersWithoutLocation_separatedWithNewlines_returnsTwo() {
        String userParams = "-Dversion=1\n-Dtest=1";
        List<String> expected = List.of("-Dversion=1", "-Dtest=1");

        List<String> actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParametersWithoutLocation(userParams);

        assertThat(actual.size()).isEqualTo(2);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void parseUserSpecifiedAlignmentParametersWithoutLocation_separatedWithNewlinesAndExtraTrailingNewline_returnsTwo() {
        String userParams = "-Dversion=1\n-Dtest=1\n";
        List<String> expected = List.of("-Dversion=1", "-Dtest=1");

        List<String> actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParametersWithoutLocation(userParams);

        assertThat(actual.size()).isEqualTo(2);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void parseUserSpecifiedAlignmentParametersWithoutLocation_newlineInUsersParameter_isTranslatedIntoSpace() {
        String userParams = "-Dfoo='bar\nbaz'";
        List<String> expected = List.of("-Dfoo=bar baz");

        List<String> actual = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParametersWithoutLocation(userParams);

        assertThat(actual.size()).isEqualTo(1);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void getJavaLocation_noJavaVersionOverride_defaultIsUsed() {
        assertThat(CommonManipulatorConfigUtils.getJavaLocation(log, Collections.emptyList())).isEqualTo(
                CommonManipulatorConfigUtils.getJavaOfVersion(CommonManipulatorConfigUtils.DEFAULT_JAVA_VERSION));
    }

    @Test
    void getJavaLocation_java17VersionOverrideGiven_overrideIsUsedForRepour() {
        assertThat(CommonManipulatorConfigUtils.getJavaLocation(log, List.of("-DRepour_Java=17")))
                .isEqualTo(CommonManipulatorConfigUtils.getJavaOfVersion("17"));
        List<LogRecord> logRecords = LogCollectingTestResource.current().getRecords();
        assertTrue(
                logRecords.stream()
                        .anyMatch(
                                r -> LogCollectingTestResource.format(r)
                                        .contains("-DRepour_Java is deprecated")));
    }

    @Test
    void getJavaLocation_java17VersionOverrideGiven_overrideIsUsedForReqour() {
        assertThat(CommonManipulatorConfigUtils.getJavaLocation(log, List.of("-DReqour_Java=17")))
                .isEqualTo(CommonManipulatorConfigUtils.getJavaOfVersion("17"));
    }

    @Test
    void getJavaLocation_java24VersionOverrideGiven_overrideIsUsed() {
        assertThat(CommonManipulatorConfigUtils.getJavaLocation(log, List.of("-DRepour_Java=24")))
                .hasToString("/usr/lib/jvm/java-24-temurin-jdk/bin/java");
    }

    @Test
    void getJavaLocation_java8VersionOverrideGiven_overrideIsUsed() {
        assertThat(CommonManipulatorConfigUtils.getJavaLocation(log, List.of("-DRepour_Java=1.8.0")))
                .isEqualTo(CommonManipulatorConfigUtils.getJavaOfVersion("1.8.0"));
    }

    @Test
    void getJavaLocation_invalidJavaVersionOverrideGiven_throwsException() {
        assertThatThrownBy(() -> CommonManipulatorConfigUtils.getJavaLocation(log, List.of("-DRepour_Java=invalid")))
                .isInstanceOf(AdjusterException.class)
                .hasMessage("Invalid Java version 'invalid' provided.");
    }
}