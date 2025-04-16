/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.jboss.pnc.common.http.PNCHttpClient;
import org.jboss.pnc.reqour.config.ConfigUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class BeanFactory {

    @Produces
    @ApplicationScoped
    public PNCHttpClient pncHttpClient(ObjectMapper objectMapper, ConfigUtils configUtils) {
        return new PNCHttpClient(objectMapper, configUtils.getPncHttpClientConfig());
    }
}
