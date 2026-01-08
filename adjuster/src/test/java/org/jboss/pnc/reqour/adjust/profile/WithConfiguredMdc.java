/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.profile;

import java.util.Map;

import org.jboss.pnc.api.constants.MDCHeaderKeys;
import org.jboss.pnc.reqour.config.core.ConfigConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTestProfile;

public class WithConfiguredMdc extends WithSuccessfulAlternatives implements QuarkusTestProfile {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PROCESS_CONTEXT_VALUE = "foo";
    private static final String MDC_EXAMPLE_KEY = "mdc-key";
    private static final String MDC_EXAMPLE_VALUE = "mdc-value";
    public static final Map<String, String> MDCs = Map
            .of(MDCHeaderKeys.PROCESS_CONTEXT.getMdcKey(), PROCESS_CONTEXT_VALUE, MDC_EXAMPLE_KEY, MDC_EXAMPLE_VALUE);

    @Override
    public Map<String, String> getConfigOverrides() {
        try {
            return Map.of(
                    ConfigConstants.ADJUSTER_MDC,
                    objectMapper.writeValueAsString(MDCs));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
