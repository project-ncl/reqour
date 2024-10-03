/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.callbacksender;

import jakarta.validation.Valid;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponse;

/**
 * Sending the callback to the location provided by the notifier of the operation.
 */
public interface CallbackSender {

    /**
     * Send the {@link RepositoryCloneResponse} callback to the REST endpoint specified by the {@link Request} object.
     *
     * @param request request
     * @param callback callback to send
     */
    void sendRepositoryCloneCallback(Request request, @Valid RepositoryCloneResponse callback);

    /**
     * Send the {@link InternalSCMCreationResponse} callback to the REST endpoint specified by the {@link Request}
     * object.
     *
     * @param request request
     * @param callback callback to send
     */
    void sendInternalSCMRepositoryCreationCallback(Request request, @Valid InternalSCMCreationResponse callback);
}
