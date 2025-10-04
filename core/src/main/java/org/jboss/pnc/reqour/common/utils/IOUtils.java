/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.common.exceptions.ResourceNotFoundException;

import lombok.NonNull;

public class IOUtils {
    private static final Pattern QUOTABLE_PATTERN = Pattern.compile("[\\s\"]");
    private static final Pattern ESCAPE_PATTERN = Pattern.compile("([\"\\\\])");

    public static void ignoreOutput(String s) {
    }

    public static Path createTempDirForCloning() {
        return createTempDir("clone-", "cloning");
    }

    public static Path createTempRandomDirForAdjust() {
        return createTempDir("adjust-", "adjust");
    }

    public static Path createTempDir(String prefix, String activity) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create temporary directory for " + activity, e);
        }
    }

    public static void deleteTempDir(Path dir) throws IOException {
        FileUtils.deleteDirectory(dir.toFile());
    }

    public static long countLines(String text) {
        return text.lines().count();
    }

    public static List<String> splitByNewLine(String text) {
        return text.lines().toList();
    }

    public static void validateResourceAtPathExists(Path path, String errorMessageTemplate) {
        if (Files.notExists(path)) {
            throw new ResourceNotFoundException(String.format(errorMessageTemplate, path));
        }
    }

    public static String readFileContent(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read content of file '%s'", file), e);
        }
    }

    public static boolean fileContainsString(Path file, String patternAsString) {
        try {
            Pattern pattern = Pattern.compile(patternAsString);
            Matcher matcher = pattern.matcher(Files.readString(file));
            return matcher.find();
        } catch (IOException e) {
            throw new RuntimeException("Unable to read content of the file " + file);
        }
    }

    public static String unquote(@NonNull String text) {
        return (text.startsWith("\"") && text.endsWith("\"")) ? text.substring(1, text.length() - 1) : text;
    }

    public static StringBuilder quoteIfNeeded(StringBuilder result, String v) {
        if (QUOTABLE_PATTERN.matcher(v).find()) {
            result.append('"').append(ESCAPE_PATTERN.matcher(v).replaceAll("\\\\$1")).append('"');
        } else {
            result.append(v);
        }
        return result;
    }

    public static AdjustRequest unescapeUserAlignmentParameters(AdjustRequest adjustRequest) {
        if (adjustRequest.getBuildConfigParameters() == null) {
            return adjustRequest;
        }
        var buildConfigParameters = new HashMap<>(adjustRequest.getBuildConfigParameters());

        String userAlignmentParameters = buildConfigParameters
                .get(BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS);
        if (userAlignmentParameters == null) {
            return adjustRequest;
        }

        String adjustedUserAlignmentParameters = userAlignmentParameters.replace("\\$", "$");
        buildConfigParameters
                .put(BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS, adjustedUserAlignmentParameters);

        return adjustRequest.toBuilder().buildConfigParameters(buildConfigParameters).build();
    }
}
