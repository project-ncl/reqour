/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

/**
 * Alignment suffix configuration.
 */
public interface SuffixConfiguration {

    String permanent();

    String temporaryPrefix();
}
