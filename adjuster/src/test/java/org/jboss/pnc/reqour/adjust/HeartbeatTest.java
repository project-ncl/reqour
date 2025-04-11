/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jboss.pnc.reqour.adjust.profile.WithSuccessfulAlternatives;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.jboss.pnc.reqour.common.TestDataSupplier.BIFROST_FINAL_LOG_UPLOAD_PATH;
import static org.jboss.pnc.reqour.common.TestDataSupplier.CALLBACK_PATH;

@QuarkusTest
@TestProfile(WithSuccessfulAlternatives.class)
@ConnectWireMock
public class HeartbeatTest {

    @Inject
    AdjustTestUtils adjustTestUtils;

    WireMock wireMock;

    @Inject
    @TopCommand
    App app;

    @BeforeEach
    void setUp() {
        wireMock.register(WireMock.post(adjustTestUtils.getHeartbeatPath()).willReturn(WireMock.ok()));
        wireMock.register(WireMock.post(CALLBACK_PATH).willReturn(WireMock.ok()));
        wireMock.register(WireMock.post(BIFROST_FINAL_LOG_UPLOAD_PATH).willReturn(WireMock.ok()));
    }

    @Test
    void run_appRunning_heartbeatIsSent() throws InterruptedException {
        app.run();

        Thread.sleep(2_500);
        wireMock.verifyThat(
                // heartbeats sent at seconds 0, 1, 2 (but possibly more times, since app's running time itself)
                new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN_OR_EQUAL, 3),
                WireMock.postRequestedFor(WireMock.urlEqualTo(adjustTestUtils.getHeartbeatPath())));
    }
}
