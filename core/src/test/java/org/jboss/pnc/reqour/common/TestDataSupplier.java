/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common;

import static org.jboss.pnc.reqour.common.TestUtils.createTranslateRequestFromExternalUrl;
import static org.jboss.pnc.reqour.common.TestUtils.createTranslateResponseFromExternalUrl;

import java.net.URI;
import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Namespace;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.ProtectedTag;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.reqour.config.GitProviderConfig;

public class TestDataSupplier {

    public static final String CALLBACK_PATH = "/callback";
    public static final String BIFROST_FINAL_LOG_UPLOAD_PATH = "/final-log/upload";
    public static final String TASK_ID = "task-id";

    public static class Translation {

        private static final String INTERNAL_URL = ConfigProvider.getConfig()
                .getValue("reqour.git.git-providers.gitlab.git-url-internal-template", String.class);

        public static TranslateResponse httpsWithOrganizationAndGitSuffix() {
            return createTranslateResponseFromExternalUrl(
                    "https://github.com/project/repo.git",
                    getInternalUrlWithOrganization());
        }

        public static TranslateRequest withoutRepository() {
            return createTranslateRequestFromExternalUrl("http://github.com");
        }

        public static String getInternalUrlWithOrganization() {
            return INTERNAL_URL + "project/repo.git";
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

    public static class InternalSCM {

        public static final String WORKSPACE_NAME = "test-workspace";
        public static final long WORKSPACE_ID = 1L;
        public static final String DIFFERENT_WORKSPACE_NAME = "different-workspace";
        public static final long DIFFERENT_WORKSPACE_ID = 2L;
        public static final String PROJECT_NAME = "project";
        public static final long PROJECT_ID = 42L;
        public static final long DIFFERENT_PROJECT_ID = 100L;

        public static Group workspaceGroup() {
            return new Group().withId(WORKSPACE_ID).withName(WORKSPACE_NAME).withFullPath(WORKSPACE_NAME);
        }

        public static Group differentWorkspaceGroup() {
            return new Group().withId(DIFFERENT_WORKSPACE_ID)
                    .withName(DIFFERENT_WORKSPACE_NAME)
                    .withFullPath(WORKSPACE_NAME + "/" + DIFFERENT_WORKSPACE_NAME)
                    .withParentId(WORKSPACE_ID);
        }

        public static Project projectFromTestWorkspace() {
            Project project = new Project().withId(42L)
                    .withName(PROJECT_NAME)
                    .withNamespace(
                            new Namespace().withId(WORKSPACE_ID).withName(WORKSPACE_NAME).withFullPath(WORKSPACE_NAME));
            project.setPathWithNamespace(WORKSPACE_NAME + "/" + PROJECT_NAME);

            return project;
        }

        public static Project projectFromDifferentWorkspace() {
            Project project = new Project().withId(DIFFERENT_PROJECT_ID)
                    .withName(PROJECT_NAME)
                    .withNamespace(
                            new Namespace().withId(DIFFERENT_WORKSPACE_ID)
                                    .withName(DIFFERENT_WORKSPACE_NAME)
                                    .withFullPath(WORKSPACE_NAME + "/" + DIFFERENT_WORKSPACE_NAME));
            project.setPathWithNamespace(WORKSPACE_NAME + "/" + DIFFERENT_WORKSPACE_NAME + "/" + PROJECT_NAME);

            return project;
        }

        public static List<ProtectedTag> protectedTags() {
            return List.of(createdProtectedTag());
        }

        public static ProtectedTag createdProtectedTag() {
            var protectedTag = new ProtectedTag();
            protectedTag.setName("*");

            return protectedTag;
        }
    }

    public static GitProviderConfig dummyGitProviderConfig() {
        return new GitProviderConfig() {
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
            public TagProtectionConfig tagProtection() {
                return null;
            }
        };
    }
}
