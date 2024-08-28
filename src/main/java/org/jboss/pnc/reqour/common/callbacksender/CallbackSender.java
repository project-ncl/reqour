/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2024-2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
