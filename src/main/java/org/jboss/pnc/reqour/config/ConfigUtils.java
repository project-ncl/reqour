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
package org.jboss.pnc.reqour.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.reqour.common.exceptions.InvalidConfigException;
import org.jboss.pnc.reqour.model.GitBackend;

import java.util.List;
import java.util.Set;

@ApplicationScoped
@Slf4j
public class ConfigUtils {

    @Inject
    ReqourConfig config;

    void validateActiveGitBackendExists(@Observes StartupEvent event) {
        String activeGitBackend = getActiveGitBackendName();
        Set<String> availableGitBackends = config.gitConfigs().gitBackendsConfig().availableGitBackends().keySet();
        if (!availableGitBackends.contains(activeGitBackend)) {
            throw new InvalidConfigException(
                    "Active git backend (" + getActiveGitBackendName()
                            + ") does not match any of the available git backends: " + availableGitBackends);
        }
        log.info("Application config successfully loaded and passed validation");
    }

    public Set<String> getAcceptableSchemes() {
        return config.gitConfigs().acceptableSchemes();
    }

    public GitBackend getActiveGitBackend() {
        String activeGitBackendName = getActiveGitBackendName();
        return GitBackend.fromConfig(
                activeGitBackendName,
                config.gitConfigs().gitBackendsConfig().availableGitBackends().get(activeGitBackendName));
    }

    private String getActiveGitBackendName() {
        return config.gitConfigs().gitBackendsConfig().activeGitBackend();
    }
}
