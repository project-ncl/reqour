/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.validation;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.validation.ConstraintValidatorContext;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.config.GitConfig;
import org.jboss.pnc.reqour.common.profile.ConfigProfile;
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