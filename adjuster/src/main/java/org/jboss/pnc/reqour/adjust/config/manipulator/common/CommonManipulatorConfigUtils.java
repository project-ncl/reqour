/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.manipulator.common;

import static org.jboss.pnc.api.constants.BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS;
import static org.jboss.pnc.api.constants.BuildConfigurationParameterKeys.BREW_BUILD_NAME;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.validation.constraints.NotNull;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.enums.AlignmentPreference;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.BuildCategoryConfig;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.model.ExecutionRootOverrides;
import org.jboss.pnc.reqour.adjust.model.LocationAndRemainingAlignmentParameters;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.common.utils.IOUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for common operations with configurations, e.g. extracting values from {@link AdjustRequest} into a
 * configuration.
 */
@Slf4j
public class CommonManipulatorConfigUtils {

    private static final String TEMPORARY_SUFFIX = "temporary";
    public static final String DEFAULT_JAVA_VERSION = "11";

    /**
     * Extract {@link BuildConfigurationParameterKeys#ALIGNMENT_PARAMETERS} from the {@link AdjustRequest}.
     */
    public static List<String> transformPncDefaultAlignmentParametersIntoList(AdjustRequest request) {
        String pncDefaultAlignmentParameters = request.getPncDefaultAlignmentParameters();
        if (pncDefaultAlignmentParameters == null) {
            return Collections.emptyList();
        }

        return Arrays.asList(pncDefaultAlignmentParameters.split(" "));
    }

    /**
     * Extract sub-folder with results and all remaining user-specified alignment parameters form the
     * {@link AdjustRequest}.
     */
    public static UserSpecifiedAlignmentParameters parseUserSpecifiedAlignmentParameters(AdjustRequest request) {
        return parseUserSpecifiedAlignmentParameters(request, "f", "file");
    }

    /**
     * Extract sub-folder with results and all remaining user-specified alignment parameters form the
     * {@link AdjustRequest}.
     */
    public static UserSpecifiedAlignmentParameters parseUserSpecifiedAlignmentParameters(
            AdjustRequest request,
            @NotNull String locationShortOption,
            @NotNull String locationLongOption) {
        Map<BuildConfigurationParameterKeys, String> buildConfigParameters = request.getBuildConfigParameters();
        String userSpecifiedAlignmentParameters = buildConfigParameters.get(ALIGNMENT_PARAMETERS);

        if (userSpecifiedAlignmentParameters == null || userSpecifiedAlignmentParameters.isBlank()) {
            return UserSpecifiedAlignmentParameters.defaultResult();
        }

        LocationAndRemainingAlignmentParameters locationAndRemainingAlignmentParameters = extractLocationFromUsersAlignmentParameters(
                userSpecifiedAlignmentParameters,
                locationShortOption,
                locationLongOption);

        List<String> parsedUserSpecifiedAlignmentParameters = parseUserSpecifiedAlignmentParametersWithoutLocation(
                locationAndRemainingAlignmentParameters.getRemainingAlignmentParameters());
        if (locationAndRemainingAlignmentParameters.getLocationOption().isEmpty()) {
            return UserSpecifiedAlignmentParameters.withoutSubFolder(parsedUserSpecifiedAlignmentParameters);
        }

        Optional<Path> location = extractLocation(
                locationShortOption,
                locationLongOption,
                locationAndRemainingAlignmentParameters.getLocationOption().get());
        return UserSpecifiedAlignmentParameters.builder()
                .locationOption(location)
                .alignmentParameters(parsedUserSpecifiedAlignmentParameters)
                .build();
    }

    private static Optional<Path> extractLocation(
            String locationShortOption,
            String locationLongOption,
            String location) {
        log.debug(
                "Extracting location from '{}' with short option '{}' and long option '{}'",
                location,
                locationShortOption,
                locationLongOption);

        CommandLineParser parser = new DefaultParser();
        final Options options = new Options();
        options.addOption(
                Option.builder()
                        .option(locationShortOption)
                        .longOpt(locationLongOption)
                        .hasArg()
                        .valueSeparator()
                        .build());
        try {
            CommandLine parseResult = parser.parse(options, new String[] { location });
            return Optional.of(Path.of(parseResult.getOptionValue(locationLongOption)));
        } catch (ParseException e) {
            log.warn("Could not parse location, returning default location");
            return UserSpecifiedAlignmentParameters.getDefaultLocationOption();
        }
    }

    static List<String> parseUserSpecifiedAlignmentParametersWithoutLocation(
            String userSpecifiedAlignmentParametersWithoutLocation) {
        if (userSpecifiedAlignmentParametersWithoutLocation.isEmpty()) {
            return Collections.emptyList();
        }

        org.apache.commons.exec.CommandLine parsedAlignmentParameters = org.apache.commons.exec.CommandLine
                .parse(userSpecifiedAlignmentParametersWithoutLocation);
        return Arrays.stream(parsedAlignmentParameters.toStrings())
                .map(IOUtils::unquote)
                .toList();
    }

    /**
     * Get the {@link ExecutionRootOverrides} from user-specified alignment parameters under the key
     * {@link BuildConfigurationParameterKeys#BREW_BUILD_NAME} in case the user provided some.
     */
    public static ExecutionRootOverrides getExecutionRootOverrides(AdjustRequest adjustRequest) {
        String brewBuildName = adjustRequest.getBuildConfigParameters().getOrDefault(BREW_BUILD_NAME, "");
        Pattern brewBuildPattern = Pattern.compile("^(?<groupId>.+):(?<artifactId>.+)$");
        Matcher brewBuildMatcher = brewBuildPattern.matcher(brewBuildName);

        if (!brewBuildMatcher.matches()) {
            return ExecutionRootOverrides.noOverrides();
        }
        return new ExecutionRootOverrides(brewBuildMatcher.group("groupId"), brewBuildMatcher.group("artifactId"));
    }

    public static String computePrefixOfVersionSuffix(AdjustRequest request, AdjustConfig adjustConfig) {
        BuildCategoryConfig buildCategoryConfig = getBuildCategoryConfig(request, adjustConfig);

        if (request.isTempBuild()) {
            return buildCategoryConfig.prefixOfSuffixVersion().map(s -> s + "-").orElse("") + TEMPORARY_SUFFIX;
        }
        return buildCategoryConfig.prefixOfSuffixVersion().orElse("");
    }

    /**
     * Strip the temporary suffix from the given prefix of the version suffix.
     */
    public static String stripTemporarySuffix(String prefixOfVersionSuffix) {
        var suffixWithoutTemporary = prefixOfVersionSuffix.replace(TEMPORARY_SUFFIX, "");
        return (suffixWithoutTemporary.endsWith("-"))
                ? suffixWithoutTemporary.substring(0, suffixWithoutTemporary.length() - 1)
                : suffixWithoutTemporary;
    }

    /**
     * Find out which rest mode should be used for this alignment.
     */
    public static String computeRestMode(AdjustRequest request, AdjustConfig adjustConfig) {
        BuildCategoryConfig buildCategoryConfig = getBuildCategoryConfig(request, adjustConfig);
        boolean isTempBuild = request.isTempBuild();
        boolean preferPersistent = isPreferPersistentEnabled(request);

        if (isTempBuild && preferPersistent) {
            return buildCategoryConfig.temporaryPreferPersistentMode();
        }
        if (isTempBuild) {
            return buildCategoryConfig.temporaryMode();
        }
        return buildCategoryConfig.persistentMode();
    }

    public static boolean isBrewPullEnabled(AdjustRequest request) {
        return request.isBrewPullActive();
    }

    public static boolean isPreferPersistentEnabled(AdjustRequest request) {
        return AlignmentPreference.PREFER_PERSISTENT.equals(request.getAlignmentPreference());
    }

    /**
     * Get the location of the java within the container environment. In case user specified none, defaults to java
     * {@value DEFAULT_JAVA_VERSION}.
     */
    public static Path getJavaLocation(List<String> userSpecifiedAlignmentParameters) {
        Optional<String> jvmLocationSystemProperty = userSpecifiedAlignmentParameters.stream()
                .filter(p -> p.startsWith("-DRepour_Java"))
                .findFirst();

        String javaVersion = jvmLocationSystemProperty.map(s -> s.split("=")[1])
                .orElse(DEFAULT_JAVA_VERSION);
        log.debug("Parsed java version: {}", javaVersion);
        if (!isValidJavaVersion(javaVersion)) {
            throw new AdjusterException(String.format("Invalid Java version '%s' provided.", javaVersion));
        }

        return getJavaOfVersion(javaVersion);
    }

    static Path getJavaOfVersion(String javaVersion) {
        return Path.of("/usr", "lib", "jvm", "java-" + javaVersion + "-openjdk", "bin", "java");
    }

    private static BuildCategoryConfig getBuildCategoryConfig(AdjustRequest request, AdjustConfig adjustConfig) {
        String buildCategory = request.getBuildConfigParameters()
                .getOrDefault(BuildConfigurationParameterKeys.BUILD_CATEGORY, "STANDARD");
        BuildCategoryConfig buildCategoryConfig = adjustConfig.buildCategories().get(buildCategory);

        if (buildCategoryConfig == null) {
            throw new AdjusterException(new IllegalArgumentException("Unknown build category specified"));
        }

        return buildCategoryConfig;
    }

    /**
     * Extract location information together with all remaining user-specified alignment parameters.<br/>
     * For instance, when having: {@code ALIGNMENT_PARAMETERS="-Dfoo=bar --file=h2/pom.xml -Dbar=baz"} and long option
     * for location is "--file", it will return:<br/>
     * - "--file=h2/pom.xml" as location - ["-Dfoo=bar", "-Dbar=baz"] as remaining args
     *
     * @param userSpecifiedAlignmentParameters user-specified alignment parameters taken from the key
     *        {@link BuildConfigurationParameterKeys#ALIGNMENT_PARAMETERS}
     * @param locationShortOption short option for location
     * @param locationLongOption long option for location
     */
    static LocationAndRemainingAlignmentParameters extractLocationFromUsersAlignmentParameters(
            String userSpecifiedAlignmentParameters,
            String locationShortOption,
            String locationLongOption) {
        String regex = String.format(
                "^.*(-%s=\\S+|-%s \\S+|--%s=\\S+|--%s \\S+).*$",
                locationShortOption,
                locationShortOption,
                locationLongOption,
                locationShortOption);
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(userSpecifiedAlignmentParameters);

        if (matcher.matches()) {
            return LocationAndRemainingAlignmentParameters.builder()
                    .locationOption(Optional.of(matcher.group(1)))
                    .remainingAlignmentParameters(userSpecifiedAlignmentParameters.replace(matcher.group(1), ""))
                    .build();
        }

        return LocationAndRemainingAlignmentParameters.builder()
                .locationOption(Optional.empty())
                .remainingAlignmentParameters(userSpecifiedAlignmentParameters)
                .build();
    }

    /**
     * Validates whether the provided java version is valid or not.<br/>
     * k
     * Currently, as a valid java version, is considered any string of the SEMVER format, where minor and patch versions
     * are optional, e.g. 21, or 1.8.0.
     *
     * @param javaVersion provided java version
     * @return true, if the provided java version is considered valid, false otherwise
     */
    private static boolean isValidJavaVersion(String javaVersion) {
        Pattern javaVersionPattern = Pattern.compile("^\\d+(?:\\.\\d+\\.\\d+)?$");
        return javaVersion.matches(javaVersionPattern.pattern());
    }
}
