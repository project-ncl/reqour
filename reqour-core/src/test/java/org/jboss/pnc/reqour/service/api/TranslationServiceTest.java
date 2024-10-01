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

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.common.exceptions.InvalidExternalUrlException;
import org.jboss.pnc.reqour.common.profile.TranslationProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@TestProfile(TranslationProfile.class)
class TranslationServiceTest {

    @Inject
    TranslationService service;

    @Test
    void externalToInternal_noProtocol_returnsResult() {
        testInvalidURL(TestDataSupplier.Translation.noProtocol(), InvalidExternalUrlException.class);
    }

    @Test
    void externalToInternal_validRepoHttpWithoutOrganizationWithoutGitSuffix_returnsResult() {
        testCorrectURL(TestDataSupplier.Translation.httpWithoutOrganizationWithoutGitSuffix());
    }

    @Test
    void externalToInternal_validRepoHttpsWithoutOrganizationWithGitSuffix_returnsResult() {
        testCorrectURL(TestDataSupplier.Translation.httpsWithoutOrganizationWithGitSuffix());
    }

    @Test
    void externalToInternal_validRepoHttpWithOrganizationWithGitSuffix_returnsResult() {
        testCorrectURL(TestDataSupplier.Translation.httpsWithOrganizationAndGitSuffix());
    }

    @Test
    void externalToInternal_validRepoGitWithOrganizationWithGitSuffix_returnsResult() {
        testCorrectURL(TestDataSupplier.Translation.gitWithOrganizationAndGitSuffix());
    }

    @Test
    void externalToInternal_validRepoGitPlusSshWithOrganizationWithGitSuffix_returnsResult() {
        testCorrectURL(TestDataSupplier.Translation.gitPlusSshWithOrganizationAndGitSuffix());
    }

    @Test
    void externalToInternal_validRepoSshWithOrganizationWithGitSuffix_returnsResult() {
        testCorrectURL(TestDataSupplier.Translation.sshWithOrganizationAndGitSuffix());
    }

    @Test
    void externalToInternal_validRepoSshWithPort_returnsResult() {
        testCorrectURL(TestDataSupplier.Translation.sshWithOrganizationAndPort());
    }

    @Test
    void externalToInternal_invalidScpLikeURL_throwsException() {
        testInvalidURL(
                TestDataSupplier.Translation.invalidScpLikeWithoutSemicolon(),
                InvalidExternalUrlException.class);
    }

    @Test
    void externalToInternal_noRepositoryProvided_throwsException() {
        testInvalidURL(TestDataSupplier.Translation.withoutRepository(), InvalidExternalUrlException.class);
    }

    @Test
    void externalToInternal_nonScpLikeWithUser_throwsException() {
        testInvalidURL(TestDataSupplier.Translation.nonScpLikeWithUser(), InvalidExternalUrlException.class);
    }

    @Test
    void externalToInternal_unknownSchema_throwsException() {
        testInvalidURL(TestDataSupplier.Translation.withUnavailableSchema(), InvalidExternalUrlException.class);
    }

    private void testCorrectURL(TranslateResponse expectedResponse) {
        TranslateResponse response = service
                .externalToInternal(TestUtils.createTranslateRequestFromExternalUrl(expectedResponse.getExternalUrl()));

        assertThat(response).isEqualTo(expectedResponse);
    }

    private void testInvalidURL(TranslateRequest request, Class<? extends Throwable> expectedException) {
        assertThatThrownBy(() -> service.externalToInternal(request)).isInstanceOf(expectedException);
    }
}