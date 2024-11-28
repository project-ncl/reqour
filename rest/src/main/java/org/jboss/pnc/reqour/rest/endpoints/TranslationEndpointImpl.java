/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.api.reqour.rest.TranslateEndpoint;
import org.jboss.pnc.reqour.service.api.TranslationService;

@ApplicationScoped
@Slf4j
public class TranslationEndpointImpl implements TranslateEndpoint {

    private final TranslationService service;

    @Inject
    public TranslationEndpointImpl(TranslationService service) {
        this.service = service;
    }

    @Override
    public TranslateResponse externalToInternal(TranslateRequest externalToInternalRequestDto) {
        String externalUrl = externalToInternalRequestDto.getExternalUrl();
        return TranslateResponse.builder()
                .externalUrl(externalUrl)
                .internalUrl(service.externalToInternal(externalUrl))
                .build();
    }
}
