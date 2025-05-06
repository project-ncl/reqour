/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.service;

/**
 * Manages messages, which will be later sent to Bifrost as a single final log.
 */
public interface FinalLogManager {

    /**
     * Add message linked with the current process context.
     * 
     * @param message message to be added
     */
    void addMessage(String message);

    /**
     * Send the final log linked with the current process context to Bifrost.
     */
    void sendMessage();
}
