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
package org.jboss.pnc.reqour.model;

import org.gitlab4j.api.models.Project;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;

/**
 * Aggregated model for storing the project together with another metadata, e.g. whether the project has already been
 * existing in the internal SCM repository before.
 */
public record GitlabGetOrCreateProjectResult(Project project, InternalSCMCreationResponse result) {
}
