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
package org.jboss.pnc.reqour.facade;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.reqour.common.exceptions.UnsupportedCloneTypeException;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.facade.api.RepositoryCloneProvider;
import org.jboss.pnc.reqour.facade.clone.CloneProvider;

@ApplicationScoped
public class RepositoryCloneProviderImpl implements RepositoryCloneProvider {

    private final ConfigUtils configUtils;
    private final Instance<CloneProvider> cloneProviders;

    @Inject
    public RepositoryCloneProviderImpl(ConfigUtils configUtils, Instance<CloneProvider> cloneProviders) {
        this.configUtils = configUtils;
        this.cloneProviders = cloneProviders;
    }

    @Override
    public void clone(RepositoryCloneRequest request) {
        if (!configUtils.getAcceptableCloneTypes().contains(request.getType())) {
            throw new UnsupportedCloneTypeException(getErrorMessage(request.getType()));
        }
        chooseProviderDelegateForRequest(request.getType()).clone(request);
    }

    private CloneProvider chooseProviderDelegateForRequest(String providerName) {
        return cloneProviders.stream()
                .filter(p -> p.name().equals(providerName))
                .findFirst()
                .orElseThrow(() -> new UnsupportedCloneTypeException(getErrorMessage(providerName)));
    }

    private String getErrorMessage(String providerType) {
        return String.format(
                "Clone type (%s) not supported. Available clone types are: %s",
                providerType,
                configUtils.getAcceptableCloneTypes());
    }
}
