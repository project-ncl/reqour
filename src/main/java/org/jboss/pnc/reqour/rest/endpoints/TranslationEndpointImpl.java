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
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.api.reqour.dto.rest.TranslateEndpoint;
import org.jboss.pnc.reqour.facade.api.TranslationProvider;

@ApplicationScoped
@Slf4j
public class TranslationEndpointImpl implements TranslateEndpoint {

    private final TranslationProvider provider;

    @Inject
    public TranslationEndpointImpl(TranslationProvider provider) {
        this.provider = provider;
    }

    @Override
    public TranslateResponse externalToInternal(TranslateRequest externalToInternalRequestDto) {
        return provider.externalToInternal(externalToInternalRequestDto);
    }
}
