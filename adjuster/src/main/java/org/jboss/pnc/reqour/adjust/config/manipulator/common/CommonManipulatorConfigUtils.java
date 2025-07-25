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
import java.util.function.Predicate;
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
import org.jboss.pnc.reqour.adjust.model.LocationAndRemainingArgsOptions;
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

        LocationAndRemainingArgsOptions optionsSplit = extractLocationFromUsersAlignmentParameters(
                userSpecifiedAlignmentParameters,
                locationShortOption,
                locationLongOption);

        List<String> parsedUserSpecifiedAlignmentParameters = parseUserSpecifiedAlignmentParametersWithoutLocation(
                optionsSplit.getRemainingArgs());
        if (optionsSplit.getLocationOption().isEmpty()) {
            return UserSpecifiedAlignmentParameters.withoutSubFolder(parsedUserSpecifiedAlignmentParameters);
        }

        Path folderWithResultsFile = computeFolderWithResults(
                locationShortOption,
                locationLongOption,
                optionsSplit.getLocationOption().get());
        return UserSpecifiedAlignmentParameters.builder()
                .subFolderWithResults(folderWithResultsFile)
                .alignmentParameters(parsedUserSpecifiedAlignmentParameters)
                .build();
    }

    private static Path computeFolderWithResults(
            String locationShortOption,
            String locationLongOption,
            String location) {
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
            CommandLine parseResult = parser.parse(options, new String[] { location });
            return extractFolderFromFile(parseResult.getOptionValue(FILE_ARG_NAME));
        } catch (ParseException e) {
            log.warn("Could not parse alignment parameters, returning default");
            return UserSpecifiedAlignmentParameters.getDefaultSubFolder();
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

    /**
     * Extract user-specified alignment parameters from the request.
     */
    public static List<String> getUserSpecifiedAlignmentParameters(AdjustRequest adjustRequest) {
        Map<BuildConfigurationParameterKeys, String> buildConfigParameters = adjustRequest.getBuildConfigParameters();
        String userSpecifiedAlignmentParameters = buildConfigParameters.getOrDefault(ALIGNMENT_PARAMETERS, "");

        List<String> parametersSplitted = List.of(userSpecifiedAlignmentParameters.split(" "));
        if (parametersSplitted.stream().filter(p -> !p.isBlank()).anyMatch(Predicate.not(p -> p.startsWith("-")))) {
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
    static LocationAndRemainingArgsOptions extractLocationFromUsersAlignmentParameters(
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
                    .remainingArgs(userSpecifiedAlignmentParameters.replace(matcher.group(1), ""))
                    .build();
        }

        return LocationAndRemainingArgsOptions.builder()
                .locationOption(Optional.empty())
                .remainingArgs(userSpecifiedAlignmentParameters)
                .build();
    }

    private static Path extractFolderFromFile(String filePath) {
        log.debug("Extracting folder from {}", filePath);

        if (filePath == null || !filePath.contains("/")) {
            return UserSpecifiedAlignmentParameters.getDefaultSubFolder();
        }

        return Path.of(filePath.substring(0, filePath.lastIndexOf("/")));
    }
}
