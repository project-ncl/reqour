/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.api.reqour.rest.TranslateEndpoint;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.jboss.pnc.reqour.service.api.TranslationService;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class TranslationEndpointImpl implements TranslateEndpoint {

    private final TranslationService service;
    private final Logger userLogger;

    public TranslationEndpointImpl(Instance<TranslationService> service, @UserLogger Logger logger) {
        this.service = service.get();
        this.userLogger = logger;
    }

    @Override
    public TranslateResponse externalToInternal(TranslateRequest externalToInternalRequestDto) {
        userLogger.info("Translate request: {}", externalToInternalRequestDto);

        String externalUrl = externalToInternalRequestDto.getExternalUrl();
        return TranslateResponse.builder()
                .externalUrl(externalUrl)
                .internalUrl(service.externalToInternal(externalUrl))
                .build();
    }
}
