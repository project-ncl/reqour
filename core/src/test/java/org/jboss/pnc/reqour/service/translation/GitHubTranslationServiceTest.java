/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.translation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.pnc.reqour.common.profile.WithGitHubProvider;
import org.jboss.pnc.reqour.service.api.TranslationService;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(WithGitHubProvider.class)
class GitHubTranslationServiceTest {

    @Inject
    Instance<TranslationService> service;

    @Test
    void externalToInternal_repositoryWithoutGroup_returnsRepositoryInInternalGroupWithUnalignedName() {
        String externalUrl = "https://github.com/repo.git";

        assertThat(service.get().externalToInternal(externalUrl)).isEqualTo("git@localhost:test-organization/repo.git");
    }

    @Test
    void externalToInternal_repositoryInNonInternalGroup_returnsRepositoryInInternalGroupWithAlignedName() {
        String externalUrl = "https://gitlab.com/organization/repo.git";

        assertThat(service.get().externalToInternal(externalUrl))
                .isEqualTo("git@localhost:test-organization/organization-repo.git");
    }

    @Test
    void externalToInternal_repositoryInInternalGroup_returnsRepositoryInInternalGroupWithUnalignedName() {
        String externalUrl = "https://gitlab.com/test-organization/repo.git";

        assertThat(service.get().externalToInternal(externalUrl))
                .isEqualTo("git@localhost:test-organization/repo.git");
    }
}
