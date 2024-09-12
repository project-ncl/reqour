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
package org.jboss.pnc.reqour.common.exceptions;

import jakarta.validation.ValidationException;

/**
 * Exception thrown in case invalid project path is given.<br/>
 * Project path is used for instance as {@link org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest#project}.<br/>
 * Valid project paths are:<br/>
 * 1) 'project'<br/>
 * 2) 'subgroup/project'
 */
public class InvalidProjectPathException extends ValidationException {

    public InvalidProjectPathException(String message) {
        super(message);
    }
}