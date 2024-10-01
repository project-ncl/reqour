/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.api;

import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;

public interface TranslationService {

    /**
     * Translates the external URL (provided in the request) into corresponding internal URL.
     *
     * @param request translation request containing (at least) external URL
     * @return translation response containing (at least) corresponding internal URL
     */
    TranslateResponse externalToInternal(TranslateRequest request);
}
