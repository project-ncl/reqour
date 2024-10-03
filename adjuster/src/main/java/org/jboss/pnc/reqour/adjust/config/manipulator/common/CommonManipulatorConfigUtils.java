/**
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
import org.jboss.pnc.reqour.adjust.exception.InvalidConfigException;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.adjust.model.LocationAndRemainingArgsOptions;

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

@Slf4j
public class CommonManipulatorConfigUtils {

    private static final String TEMPORARY_SUFFIX = "temporary";
    private static final String FILE_ARG_NAME = "file";

    public static List<String> transformPncDefaultAlignmentParametersIntoList(AdjustRequest request) {
        String pncDefaultAlignmentParameters = request.getPncDefaultAlignmentParameters();
        if (pncDefaultAlignmentParameters == null) {
            return Collections.emptyList();
        }

        return Arrays.asList(pncDefaultAlignmentParameters.split(" "));
    }

    public static UserSpecifiedAlignmentParameters parseUserSpecifiedAlignmentParameters(AdjustRequest request) {
        return parseUserSpecifiedAlignmentParameters(request, "f", "file");
    }

    public static UserSpecifiedAlignmentParameters parseUserSpecifiedAlignmentParameters(
            AdjustRequest request,
            @NotNull String locationShortOption,
            @NotNull String locationLongOption) {
        Map<BuildConfigurationParameterKeys, String> buildConfigParameters = request.getBuildConfigParameters();
        String userSpecifiedAlignmentParameters = buildConfigParameters.get(ALIGNMENT_PARAMETERS);

        if (userSpecifiedAlignmentParameters == null) {
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
                    .subFolder(extractFolderFromFile(parseResult.getOptionValue(FILE_ARG_NAME)))
                    .build();
        } catch (ParseException e) {
            log.warn("Could not parse alignment parameters, returning default");
            return UserSpecifiedAlignmentParameters.defaultResult();
        }
    }

    public static List<String> getExtraAdjustmentParameters(AdjustRequest adjustRequest) {
        Map<BuildConfigurationParameterKeys, String> buildConfigParameters = adjustRequest.getBuildConfigParameters();
        String userSpecifiedAlignmentParameters = buildConfigParameters.getOrDefault(ALIGNMENT_PARAMETERS, "");

        List<String> parametersSplitted = List.of(userSpecifiedAlignmentParameters.split(" "));
        if (parametersSplitted.stream().anyMatch(Predicate.not(p -> p.startsWith("-")))) {
            throw new InvalidConfigException(
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

    public static String stripTemporarySuffix(String prefixOfSuffix) {
        var suffixWithoutTemporary = prefixOfSuffix.replace(TEMPORARY_SUFFIX, "");
        return (suffixWithoutTemporary.endsWith("-"))
                ? suffixWithoutTemporary.substring(0, suffixWithoutTemporary.length() - 1)
                : suffixWithoutTemporary;
    }

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

    public static Path getJvmLocation(List<String> userSpecifiedAlignmentParameters) {
        Optional<String> jvmLocationSystemProperty = userSpecifiedAlignmentParameters.stream()
                .filter(p -> p.startsWith("-DRepour_Java"))
                .findFirst();
        if (jvmLocationSystemProperty.isEmpty()) {
            return Path.of("java");
        }

        int jvmVersion = Integer.parseInt(jvmLocationSystemProperty.get().split("=")[1]);
        return Path.of("/usr", "lib", "jvm", "java-" + jvmVersion + "-openjdk", "bin", "java");
    }

    private static BuildCategoryConfig getBuildCategoryConfig(AdjustRequest request, AdjustConfig adjustConfig) {
        String buildCategory = request.getBuildConfigParameters()
                .getOrDefault(BuildConfigurationParameterKeys.BUILD_CATEGORY, "STANDARD");
        BuildCategoryConfig buildCategoryConfig = adjustConfig.buildCategories().get(buildCategory);

        if (buildCategoryConfig == null) {
            log.warn("Got unknown build category: {}", buildCategory);
            throw new IllegalArgumentException("Unknown build category specified");
        }

        return buildCategoryConfig;
    }

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
