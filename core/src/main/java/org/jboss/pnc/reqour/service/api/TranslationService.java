/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.api;

public interface TranslationService {

    /**
     * Translates the external URL into corresponding internal URL.
     *
     * @param externalUrl external URL
     * @return corresponding internal URL
     */
    String externalToInternal(String externalUrl);
}
