/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

@ApplicationScoped
public class AdjustTestUtils {

    @ConfigProperty(name = "test.location.requests.dir")
    Path requestsLocation;

    @ConfigProperty(name = "test.heartbeat.path")
    @Getter
    String heartbeatPath;

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
     * Asserts whether the system property in the given command has exactly expected values in the concrete order. The
     * order is ascending (by priority). <br/>
     * <br/>
     * For instance, suppose the following: <br/>
     * {@code systemProperty="foo"} <br/>
     * {@code command=["java", "-jar", "foo/cli.jar", "-Dfoo=default", "-Dfoo=user"]} <br/>
     * <br/>
     * Then, in order for test to pass, the following has to hold: {@code expectedValues = ["default", "user"]}.
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

    /**
     * Asserts whether system properties do appear *exactly* the corresponding times in the given command. <br/>
     * <br/>
     * For instance, suppose the following command: <br/>
     * {@code systemProperty="foo"} <br/>
     * {@code command=["java", "-jar", "foo/cli.jar", "-Dfoo=default", "-Dfoo=user", "-Dbar=baz"]} <br/>
     * <br/>
     * Then, in order for test to pass, the following has to hold: {@code expectedSystemPropertiesCounts = {"foo": 2,
     * "bar": 1}.
     */
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
