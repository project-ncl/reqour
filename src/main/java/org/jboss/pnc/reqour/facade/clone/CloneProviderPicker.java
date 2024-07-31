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
package org.jboss.pnc.reqour.facade.clone;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.pnc.reqour.common.exceptions.UnsupportedCloneTypeException;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.facade.api.CloneProvider;

import java.util.stream.Collectors;

/**
 * Picks the correct {@link CloneProvider} based on the provider name obtained from the
 * {@link org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest}.
 */
@ApplicationScoped
public class CloneProviderPicker {

    private final Instance<CloneProvider> cloneProviders;

    @Inject
    public CloneProviderPicker(Instance<CloneProvider> cloneProviders, ConfigUtils configUtils) {
        this.cloneProviders = cloneProviders;
    }

    public CloneProvider pickProvider(String provider) {
        return cloneProviders.stream()
                .filter(p -> p.name().equals(provider))
                .findFirst()
                .orElseThrow(() -> new UnsupportedCloneTypeException(getErrorMessage(provider)));
    }

    private String getErrorMessage(String providerType) {
        return String.format(
                "Clone type '%s' is not supported. Available clone types are: %s.",
                providerType,
                cloneProviders.stream().map(CloneProvider::name).collect(Collectors.joining(", ")));
    }
}
