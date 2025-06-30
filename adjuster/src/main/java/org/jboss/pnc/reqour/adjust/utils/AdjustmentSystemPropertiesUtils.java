/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Utility class for work with adjustment system properties.
 */
public class AdjustmentSystemPropertiesUtils {

    public static String createAdjustmentSystemProperty(AdjustmentSystemPropertyName name, Object value) {
        return String.format("%s=%s", name.getCliRepresentation(), value);
    }

    /**
     * In the given {@code Stream<String>}, it tries to find the occurrence for 'name=<value>'. In case there is no '=',
     * it will apply {@code defaultValue}.<br/>
     * For instance, when finding '-Dfoo' in '-Dbar -Dfoo=bar', it returns 'bar'. It prefers the first occurrence over
     * any other, i.e., it returns 'bar' in case '-Dfoo=bar -Dfoo=baz'.
     * 
     * @param name system property name
     * @param streams string stream, in which to find the value assigned to the given name
     * @param defaultValue value to return in case there is no '='
     */
    public static Optional<String> getSystemPropertyValue(String name, Stream<String> streams, String defaultValue) {
        return streams.filter(p -> p.startsWith(name)).findFirst().map(p -> {
            try {
                return p.split("=")[1];
            } catch (ArrayIndexOutOfBoundsException _e) {
                return defaultValue;
            }
        });
    }

    public static Optional<String> getSystemPropertyValue(
            AdjustmentSystemPropertyName name,
            Stream<String> streams,
            String defaultValue) {
        return getSystemPropertyValue(name.getCliRepresentation(), streams, defaultValue);
    }

    public static Optional<String> getSystemPropertyValue(AdjustmentSystemPropertyName name, Stream<String> streams) {
        return getSystemPropertyValue(name.getCliRepresentation(), streams, "");
    }

    public static Stream<String> joinSystemPropertiesListsIntoStream(List<List<String>> lists) {
        return lists.stream().flatMap(Collection::stream);
    }

    public static List<String> joinSystemPropertiesListsIntoList(List<List<String>> lists) {
        return joinSystemPropertiesListsIntoStream(lists).toList();
    }

    public enum AdjustmentSystemPropertyName {

        REST_MODE("restMode"),
        VERSION_INCREMENTAL_SUFFIX("versionIncrementalSuffix"),
        BREW_PULL_ACTIVE("restBrewPullActive"),
        VERSION_SUFFIX_ALTERNATIVES("versionSuffixAlternatives"),
        MANIPULATION_DISABLE("manipulation.disable");

        private final String cliName;

        private AdjustmentSystemPropertyName(String cliName) {
            this.cliName = cliName;
        }

        private String getCliRepresentation() {
            return String.format("-D%s", cliName);
        }
    }
}
