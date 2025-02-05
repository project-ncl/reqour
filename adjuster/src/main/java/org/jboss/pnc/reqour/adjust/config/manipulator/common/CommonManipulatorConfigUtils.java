/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.manipulator.common;

import lombok.extern.slf4j.Slf4j;
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
import org.jboss.pnc.reqour.adjust.model.LocationAndRemainingArgsOptions;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.jboss.pnc.api.constants.BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS;
import static org.jboss.pnc.api.constants.BuildConfigurationParameterKeys.BREW_BUILD_NAME;

/**
 * Utility class for common operations with configurations, e.g. extracting values from {@link AdjustRequest} into a
 * configuration.
 */
@Slf4j
public class CommonManipulatorConfigUtils {

    private static final String TEMPORARY_SUFFIX = "temporary";
    private static final String FILE_ARG_NAME = "file";
    private static final int DEFAULT_JAVA_VERSION = 11;

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

        LocationAndRemainingArgsOptions optionsSplitted = extractLocationFromUsersAlignmentParameters(
                userSpecifiedAlignmentParameters,
                locationShortOption,
                locationLongOption);

        if (optionsSplitted.getLocationOption().isEmpty()) {
            return UserSpecifiedAlignmentParameters.withoutSubFolder(userSpecifiedAlignmentParameters);
        }

        CommandLineParser parser = new DefaultParser();
        final Options options = new Options();
        options.addOption(
                Option.builder()
                        .option(locationShortOption)
                        .longOpt(locationLongOption)
                        .argName(FILE_ARG_NAME)
                        .hasArg()
                        .valueSeparator()
                        .build());
        try {
            CommandLine parseResult = parser.parse(options, optionsSplitted.getLocationOption().get().split(" "));
            return UserSpecifiedAlignmentParameters.builder()
                    .alignmentParameters(optionsSplitted.getRemainingArgs())
                    .subFolderWithResults(extractFolderFromFile(parseResult.getOptionValue(FILE_ARG_NAME)))
                    .build();
        } catch (ParseException e) {
            log.warn("Could not parse alignment parameters, returning default");
            return UserSpecifiedAlignmentParameters.defaultResult();
        }
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

    /**
     * Extract user-specified alignment parameters from the request.
     */
    public static List<String> getUserSpecifiedAlignmentParameters(AdjustRequest adjustRequest) {
        Map<BuildConfigurationParameterKeys, String> buildConfigParameters = adjustRequest.getBuildConfigParameters();
        String userSpecifiedAlignmentParameters = buildConfigParameters.getOrDefault(ALIGNMENT_PARAMETERS, "");

        List<String> parametersSplitted = List.of(userSpecifiedAlignmentParameters.split(" "));
        if (parametersSplitted.stream().anyMatch(Predicate.not(p -> p.startsWith("-")))) {
            throw new AdjusterException(
                    "Parameters which do not start with '-' are not allowed. Given: '"
                            + userSpecifiedAlignmentParameters + "'.");
        }

        return parametersSplitted;
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

        int javaVersion = jvmLocationSystemProperty.map(s -> Integer.parseInt(s.split("=")[1]))
                .orElse(DEFAULT_JAVA_VERSION);
        return getJavaOfVersion(javaVersion);
    }

    private static Path getJavaOfVersion(int javaVersion) {
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
    private static LocationAndRemainingArgsOptions extractLocationFromUsersAlignmentParameters(
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
            return LocationAndRemainingArgsOptions.builder()
                    .locationOption(Optional.of(matcher.group(1)))
                    .remainingArgs(
                            Stream.of(userSpecifiedAlignmentParameters.replace(matcher.group(1), ""))
                                    .filter(Predicate.not(String::isBlank))
                                    .toList())
                    .build();
        }

        return LocationAndRemainingArgsOptions.builder()
                .locationOption(Optional.empty())
                .remainingArgs(List.of(userSpecifiedAlignmentParameters.split(" ")))
                .build();
    }

    private static Path extractFolderFromFile(String filePath) {
        log.debug("Extracting folder from {}", filePath);

        if (filePath == null || !filePath.contains("/")) {
            return Path.of("");
        }

        return Path.of(filePath.substring(0, filePath.lastIndexOf("/")));
    }
}
