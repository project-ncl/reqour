/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.pnc.reqour.common.exceptions.InvalidExternalUrlException;
import org.jboss.pnc.reqour.common.profile.TranslationProfile;
import org.jboss.pnc.reqour.service.api.TranslationService;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TranslationProfile.class)
class GitlabTranslationServiceTest {

    @Inject
    Instance<TranslationService> service;

    @Test
    void externalToInternal_repositoryNotInMaintainerGroup_returnsRepositoryWithGroupUnderMaintainerGroup() {
        String externalUrl = "https://gitlab.com/organization/repo.git";

        assertThat(service.get().externalToInternal(externalUrl))
                .isEqualTo("git@localhost:test-workspace/organization/repo.git");
    }

    @Test
    void externalToInternal_repositoryWithoutGroup_returnsRepositoryUnderMaintainerGroup() {
        String externalUrl = "https://gitlab.com/repo.git";

        assertThat(service.get().externalToInternal(externalUrl)).isEqualTo("git@localhost:test-workspace/repo.git");
    }

    @Test
    void externalToInternal_repositoryInMaintainerGroup_returnsRepositoryUnderMaintainerGroup() {
        String externalUrl = "https://gitlab.com/test-workspace/repo.git";

        assertThat(service.get().externalToInternal(externalUrl)).isEqualTo("git@localhost:test-workspace/repo.git");
    }

    @Test
    void externalToInternal_unknownSchema_throwsException() {
        assertThatThrownBy(() -> service.get().externalToInternal("httprd://github.com/repo.git"))
                .isInstanceOf(InvalidExternalUrlException.class);
    }
}