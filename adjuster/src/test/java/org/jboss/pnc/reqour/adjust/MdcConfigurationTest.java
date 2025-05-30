/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.common.TestDataSupplier.BIFROST_FINAL_LOG_UPLOAD_PATH;
import static org.jboss.pnc.reqour.common.TestDataSupplier.CALLBACK_PATH;

import jakarta.inject.Inject;

import org.jboss.pnc.reqour.adjust.profile.WithConfiguredMdc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(WithConfiguredMdc.class)
@ConnectWireMock
public class MdcConfigurationTest {

    @Inject
    @TopCommand
    App app;

    WireMock wireMock;

    @BeforeEach
    void setUp() {
        wireMock.register(WireMock.post(BIFROST_FINAL_LOG_UPLOAD_PATH).willReturn(WireMock.ok()));
        wireMock.register(WireMock.post(CALLBACK_PATH).willReturn(WireMock.ok()));
    }

    @AfterEach
    void tearDown() {
        wireMock.resetRequests();
    }

    @Test
    void run_appRunning_mdcWereSet() {
        app.run();

        assertThat(MDC.getCopyOfContextMap()).isEqualTo(WithConfiguredMdc.MDCs);
    }
}
