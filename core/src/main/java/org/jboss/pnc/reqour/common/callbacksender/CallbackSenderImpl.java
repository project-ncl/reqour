/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.callbacksender;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponse;
import org.jboss.pnc.common.http.PNCHttpClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Sender which uses {@link PNCHttpClient} to send callbacks.
 */
@ApplicationScoped
@Slf4j
public class CallbackSenderImpl implements CallbackSender {

    private final PNCHttpClient pncHttpClient;

    @Inject
    public CallbackSenderImpl(PNCHttpClient pncHttpClient) {
        this.pncHttpClient = pncHttpClient;
    }

    @Override
    public void sendRepositoryCloneCallback(Request request, RepositoryCloneResponse callback) {
        sendCallback(request, callback);
    }

    @Override
    public void sendInternalSCMRepositoryCreationCallback(Request request, InternalSCMCreationResponse callback) {
        sendCallback(request, callback);
    }

    private void sendCallback(Request request, Object payload) {
        pncHttpClient.sendRequest(request, payload);
    }
}
