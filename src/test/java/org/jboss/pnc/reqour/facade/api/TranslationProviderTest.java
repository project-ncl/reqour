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
package org.jboss.pnc.reqour.facade.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.reqour.common.TestData;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.common.exceptions.InvalidExternalUrlException;
import org.jboss.pnc.reqour.profile.TranslationProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@TestProfile(TranslationProfile.class)
class TranslationProviderTest {

    @Inject
    TranslationProvider provider;

    @Test
    void externalToInternal_noProtocol_returnsResult() {
        testCorrectURL(TestData.noProtocol());
    }

    @Test
    void externalToInternal_validRepoHttpWithoutOrganizationWithoutGitSuffix_returnsResult() {
        testCorrectURL(TestData.httpWithoutOrganizationWithoutGitSuffix());
    }

    @Test
    void externalToInternal_validRepoHttpsWithoutOrganizationWithGitSuffix_returnsResult() {
        testCorrectURL(TestData.httpsWithoutOrganizationWithGitSuffix());
    }

    @Test
    void externalToInternal_validRepoHttpWithOrganizationWithGitSuffix_returnsResult() {
        testCorrectURL(TestData.httpsWithOrganizationAndGitSuffix());
    }

    @Test
    void externalToInternal_validRepoGitWithOrganizationWithGitSuffix_returnsResult() {
        testCorrectURL(TestData.gitWithOrganizationAndGitSuffix());
    }

    @Test
    void externalToInternal_validRepoGitPlusSshWithOrganizationWithGitSuffix_returnsResult() {
        testCorrectURL(TestData.gitPlusSshWithOrganizationAndGitSuffix());
    }

    @Test
    void externalToInternal_validRepoSshWithOrganizationWithGitSuffix_returnsResult() {
        testCorrectURL(TestData.sshWithOrganizationAndGitSuffix());
    }

    @Test
    void externalToInternal_validRepoSshWithPort_returnsResult() {
        testCorrectURL(TestData.sshWithOrganizationAndPort());
    }

    @Test
    void externalToInternal_invalidScpLikeURL_throwsException() {
        testInvalidURL(TestData.invalidScpLikeWithoutSemicolon(), InvalidExternalUrlException.class);
    }

    @Test
    void externalToInternal_noRepositoryProvided_throwsException() {
        testInvalidURL(TestData.withoutRepository(), InvalidExternalUrlException.class);
    }

    @Test
    void externalToInternal_nonScpLikeWithUser_throwsException() {
        testInvalidURL(TestData.nonScpLikeWithUser(), InvalidExternalUrlException.class);
    }

    @Test
    void externalToInternal_unknownSchema_throwsException() {
        testInvalidURL(TestData.withUnavailableSchema(), InvalidExternalUrlException.class);
    }

    private void testCorrectURL(TranslateResponse expectedResponse) {
        TranslateResponse response = provider
                .externalToInternal(TestUtils.createTranslateRequestFromExternalUrl(expectedResponse.getExternalUrl()));

        assertThat(response).isEqualTo(expectedResponse);
    }

    private void testInvalidURL(TranslateRequest request, Class<? extends Throwable> expectedException) {
        assertThatThrownBy(() -> provider.externalToInternal(request)).isInstanceOf(expectedException);
    }
}