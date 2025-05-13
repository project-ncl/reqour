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
import org.slf4j.MDC;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class FinalLogManagerImpl implements FinalLogManager {

    private final ConcurrentHashMap<String, StringBuffer> messages = new ConcurrentHashMap<>();

    @Inject
    BifrostLogUploaderWrapper bifrostUploader;

    @Override
    public void addMessage(String message) {
        final String processContext = getProcessContextValue();
        log.debug("Adding message for process context {}", processContext);

        messages.computeIfAbsent(processContext, StringBuffer::new)
                .append(message)
                .append(System.lineSeparator());
    }

    @Override
    public void sendMessage() {
        String processContext = getProcessContextValue();
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
