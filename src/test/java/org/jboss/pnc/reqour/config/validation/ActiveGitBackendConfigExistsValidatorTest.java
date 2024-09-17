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
package org.jboss.pnc.reqour.config.validation;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.validation.ConstraintValidatorContext;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.config.GitConfig;
import org.jboss.pnc.reqour.profile.ConfigProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ConfigProfile.class)
class ActiveGitBackendConfigExistsValidatorTest {

    GitConfig.GitBackendsConfig gitBackendsConfigMock;

    ConstraintValidatorContext validatorContextMock;

    @BeforeEach
    void setup() {
        // Not normal-scoped bean, @InjectMock cannot be used
        gitBackendsConfigMock = Mockito.mock(GitConfig.GitBackendsConfig.class);
        Mockito.when(gitBackendsConfigMock.availableGitBackends())
                .thenReturn(Map.of("existing", TestDataSupplier.dummyGitBackendConfig()));

        // Not normal-scoped bean, @InjectMock cannot be used
        validatorContextMock = Mockito.mock(ConstraintValidatorContext.class);
        Mockito.when(validatorContextMock.buildConstraintViolationWithTemplate(Mockito.any())).thenReturn(null);
    }

    @Test
    void isValid_whenValid_returnsTrue() {
        Mockito.when(gitBackendsConfigMock.activeGitBackend()).thenReturn("existing");

        assertTrue(new ActiveGitBackendExistsValidator().isValid(gitBackendsConfigMock, validatorContextMock));
    }

    @Test
    void isValid_whenInvalid_returnsFalse() {
        Mockito.when(gitBackendsConfigMock.activeGitBackend()).thenReturn("non-existing");

        assertFalse(new ActiveGitBackendExistsValidator().isValid(gitBackendsConfigMock, validatorContextMock));
    }
}