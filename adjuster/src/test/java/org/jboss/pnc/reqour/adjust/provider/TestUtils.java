/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationScoped
public class TestUtils {

    @ConfigProperty(name = "test.location.requests.dir")
    Path requestsLocation;

    @Inject
    ObjectMapper objectMapper;

    public AdjustRequest getAdjustRequest(Path requestInputFile) {
        try {
            return objectMapper.readValue(requestsLocation.resolve(requestInputFile).toFile(), AdjustRequest.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * TODO
     */
    public static void assertSystemPropertyHasValuesSortedByPriority(
            List<String> command,
            String systemProperty,
            List<String> expectedValues) {
        List<String> actualValues = command.stream()
                .filter(p -> p.startsWith(String.format("-D%s=", systemProperty)))
                .map(p -> p.split("=")[1])
                .toList();

        assertThat(actualValues).isEqualTo(expectedValues);
    }

    public static void assertSystemPropertiesContainExactly(
            List<String> command,
            Map<String, Integer> expectedSystemPropertiesCounts) {
        Map<String, Integer> actualSystemPropertiesCount = new HashMap<>();

        command.stream().filter(p -> p.startsWith("-D")).map(p -> p.replace("-D", "")).forEach(s -> {
            String systemPropertyName = s.split("=")[0];
            actualSystemPropertiesCount
                    .put(systemPropertyName, actualSystemPropertiesCount.getOrDefault(systemPropertyName, 0) + 1);
        });

        assertThat(actualSystemPropertiesCount).isEqualTo(expectedSystemPropertiesCounts);
    }
}
