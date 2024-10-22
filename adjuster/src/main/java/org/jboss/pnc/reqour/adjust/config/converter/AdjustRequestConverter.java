/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.spi.Converter;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;

import java.io.IOException;

@Slf4j
public class AdjustRequestConverter implements Converter<AdjustRequest> {

    // ObjectMapper from container is not initialized in this phase, hence custom
    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AdjustRequest convert(String value) throws IllegalArgumentException, NullPointerException {
        try {
            AdjustRequest request = objectMapper.readValue(value, AdjustRequest.class);
            log.debug("Parsed adjust request: {}", request);
            return request;
        } catch (IOException e) {
            throw new RuntimeException("Error occurred when reading the request from ADJUST_REQUEST env variable", e);
        }
    }
}
