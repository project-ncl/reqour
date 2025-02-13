/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.spi.Converter;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;

import java.io.IOException;

/**
 * Custom converter of the {@link AdjustRequest} config property.<br>
 * / For more info about custom converters, see
 * <a href="https://quarkus.io/guides/config-extending-support#custom-converter">Quarkus configuration guide</a>.
 */
@Slf4j
public class AdjustRequestConverter implements Converter<AdjustRequest> {

    // This converter cannot be a bean, hence own ObjectMapper
    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AdjustRequest convert(String value) throws IllegalArgumentException, NullPointerException {
        try {
            return objectMapper.readValue(value, AdjustRequest.class);
        } catch (IOException e) {
            throw new RuntimeException("Error occurred when reading the request from ADJUST_REQUEST env variable", e);
        }
    }
}
