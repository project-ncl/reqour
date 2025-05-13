/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.service;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.constants.MDCHeaderKeys;
import org.jboss.pnc.reqour.enums.FinalLogUploader;
import org.jboss.pnc.reqour.runtime.BifrostLogUploaderWrapper;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;
import org.slf4j.MDC;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class FinalLogManagerImpl implements FinalLogManager {

    private final ConcurrentHashMap<String, StringBuffer> messages = new ConcurrentHashMap<>();

    @Inject
    BifrostLogUploaderWrapper bifrostUploader;

    @Inject
    @UserLogger
    Logger userLogger;

    @Override
    public void addMessage(String message) {
        final String processContext = getProcessContextValue();
        if (processContext == null) {
            userLogger.warn(
                    "Not having any {} in MDC, wanted to add the message: '{}'",
                    MDCHeaderKeys.PROCESS_CONTEXT.getMdcKey(),
                    message);
            return;
        }

        log.debug("Adding message for process context {}", processContext);
        messages.computeIfAbsent(processContext, _processContext -> new StringBuffer())
                .append(message)
                .append(System.lineSeparator());
    }

    @Override
    public void sendMessage() {
        String processContext = getProcessContextValue();
        if (processContext == null) {
            throw new IllegalStateException(
                    String.format(
                            "Not having any %s in MDC, wanted to send a message",
                            MDCHeaderKeys.PROCESS_CONTEXT.getMdcKey()));
        }

        log.debug("Gonna send message for process context {}", processContext);
        StringBuffer sb = messages.remove(processContext);
        if (sb == null) {
            throw new IllegalArgumentException(
                    "Final log message for process context '" + processContext + "' not found.");
        }
        bifrostUploader.uploadStringFinalLog(sb.toString(), FinalLogUploader.REST);
    }

    private String getProcessContextValue() {
        return MDC.get(MDCHeaderKeys.PROCESS_CONTEXT.getMdcKey());
    }
}
