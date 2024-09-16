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
package org.jboss.pnc.reqour.common;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.models.Group;

@ApplicationScoped
public class TestWorkspaceSupplier {

    @ConfigProperty(name = "reqour.git.git-backends.available.gitlab.workspace-id")
    Long workspaceId;

    @ConfigProperty(name = "reqour.git.git-backends.available.gitlab.workspace")
    String workspaceName;

    private static Group workspaceInstance;

    private static Group differentWorkspaceInstance;

    public Group getWorkspaceGroup() {
        if (workspaceInstance == null) {
            workspaceInstance = new Group().withId(workspaceId).withName(workspaceName);
        }

        return workspaceInstance;
    }

    public Group getDifferentWorkspaceGroup() {
        if (differentWorkspaceInstance == null) {
            differentWorkspaceInstance = new Group().withId(2L)
                    .withName("different-workspace")
                    .withParentId(getWorkspaceGroup().getId());
        }

        return differentWorkspaceInstance;
    }
}
