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

import org.eclipse.microprofile.config.ConfigProvider;
import org.gitlab4j.api.models.Group;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.reqour.config.GitBackendConfig;

import java.net.URI;

import static org.jboss.pnc.reqour.common.TestUtils.createTranslateRequestFromExternalUrl;
import static org.jboss.pnc.reqour.common.TestUtils.createTranslateResponseFromExternalUrl;

public class TestData {

    public static final String TASK_ID = "task-id";

    public static class Translation {

        private static final String INTERNAL_URL = ConfigProvider.getConfig()
                .getValue("reqour.git.git-backends.available.gitlab.git-url-internal-template", String.class);

        public static TranslateResponse noProtocol() {
            return createTranslateResponseFromExternalUrl("github.com/repo", getInternalUrlWithoutOrganization());
        }

        public static TranslateResponse httpWithoutOrganizationWithoutGitSuffix() {
            return createTranslateResponseFromExternalUrl(
                    "http://github.com/repo",
                    getInternalUrlWithoutOrganization());
        }

        public static TranslateResponse httpsWithoutOrganizationWithGitSuffix() {
            return createTranslateResponseFromExternalUrl(
                    "https://gitlab.com/repo.git",
                    getInternalUrlWithoutOrganization());
        }

        public static TranslateResponse httpsWithOrganizationAndGitSuffix() {
            return createTranslateResponseFromExternalUrl(
                    "https://github.com/project/repo.git",
                    getInternalUrlWithOrganization());
        }

        public static TranslateResponse gitWithOrganizationAndGitSuffix() {
            return createTranslateResponseFromExternalUrl(
                    "git@github.com:project/repo.git",
                    getInternalUrlWithOrganization());
        }

        public static TranslateResponse gitPlusSshWithOrganizationAndGitSuffix() {
            return createTranslateResponseFromExternalUrl(
                    "git+ssh://github.com/project/repo.git",
                    getInternalUrlWithOrganization());
        }

        public static TranslateResponse sshWithOrganizationAndGitSuffix() {
            return createTranslateResponseFromExternalUrl(
                    "ssh://github.com/project/repo.git",
                    getInternalUrlWithOrganization());
        }

        public static TranslateResponse sshWithOrganizationAndPort() {
            return createTranslateResponseFromExternalUrl(
                    "ssh://github.com:22/project/repo.git",
                    getInternalUrlWithOrganization());
        }

        public static TranslateRequest invalidScpLikeWithoutSemicolon() {
            return createTranslateRequestFromExternalUrl("git@github.com/project/repo.git");
        }

        public static TranslateRequest withoutRepository() {
            return createTranslateRequestFromExternalUrl("http://github.com");
        }

        public static TranslateResponse nonScpLikeWithUser() {
            return createTranslateResponseFromExternalUrl(
                    "git@github.com/project/repo.git",
                    getInternalUrlWithOrganization());
        }

        public static TranslateRequest withUnavailableSchema() {
            return createTranslateRequestFromExternalUrl("httprd://github.com/repo.git");
        }

        private static String getInternalUrlWithoutOrganization() {
            return INTERNAL_URL + "/repo.git";
        }

        public static String getInternalUrlWithOrganization() {
            return INTERNAL_URL + "/project/repo.git";
        }
    }

    public static class Cloning {

        public static RepositoryCloneRequest withMissingTargetUrl() {
            return RepositoryCloneRequest.builder()
                    .originRepoUrl("https://github.com/project/repo")
                    .ref("main")
                    .taskId(TASK_ID)
                    .callback(
                            Request.builder()
                                    .method(Request.Method.POST)
                                    .uri(URI.create("https://example.com/operation"))
                                    .build())
                    .build();
        }
    }

    public static class InternalSCMRepositoryCreation {

        public static Group workspaceGroup() {
            String workspaceName = ConfigProvider.getConfig()
                    .getValue("reqour.git.git-backends.available.gitlab.workspace", String.class);
            Long workspaceId = ConfigProvider.getConfig()
                    .getValue("reqour.git.git-backends.available.gitlab.workspace-id", Long.class);

            return new Group().withId(workspaceId).withName(workspaceName);
        }
    }

    public static GitBackendConfig dummyGitBackendConfig() {
        return new GitBackendConfig() {
            @Override
            public String username() {
                return "";
            }

            @Override
            public String url() {
                return "";
            }

            @Override
            public String workspaceName() {
                return "";
            }

            @Override
            public long workspaceId() {
                return 0;
            }

            @Override
            public String hostname() {
                return "";
            }

            @Override
            public String readOnlyTemplate() {
                return "";
            }

            @Override
            public String readWriteTemplate() {
                return "";
            }

            @Override
            public String gitUrlInternalTemplate() {
                return "";
            }

            @Override
            public String token() {
                return "";
            }

            @Override
            public ProtectedTagsConfig protectedTags() {
                return null;
            }
        };
    }
}
