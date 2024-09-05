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
package org.jboss.pnc.reqour.service.api;

import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponseCallback;

/**
 * Clone service used for cloning of the repository to the internal repository. <br/>
 */
public interface CloneService {

    /**
     * Clone the external repository (either completely or partially - depending on the
     * {@link RepositoryCloneRequest#ref} to the internal repository.
     *
     * @param cloneRequest cloning request describing the way it should be cloned
     */
    RepositoryCloneResponseCallback clone(RepositoryCloneRequest cloneRequest);
}
