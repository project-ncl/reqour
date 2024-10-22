/**
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

    public static Optional<String> getSystemPropertyValue(String name, Stream<String> streams) {
        return streams.filter(p -> p.startsWith(name)).findFirst().map(p -> p.split("=")[1]);
    }

    public static Optional<String> getSystemPropertyValue(AdjustmentSystemPropertyName name, Stream<String> streams) {
        return getSystemPropertyValue(name.getCliRepresentation(), streams);
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
        VERSION_SUFFIX_ALTERNATIVES("versionSuffixAlternatives");

        private final String cliName;

        private AdjustmentSystemPropertyName(String cliName) {
            this.cliName = cliName;
        }

        private String getCliRepresentation() {
            return String.format("-D%s", cliName);
        }
    }
}
