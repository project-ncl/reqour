/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common;

import static org.jboss.pnc.reqour.common.TestUtils.createTranslateRequestFromExternalUrl;
import static org.jboss.pnc.reqour.common.TestUtils.createTranslateResponseFromExternalUrl;

import java.net.URI;
import java.util.Collections;
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
import org.jboss.pnc.reqour.service.githubrestapi.model.GHRuleset;
import org.jboss.pnc.reqour.service.githubrestapi.model.GHRulesetCondition;
import org.jboss.pnc.reqour.service.githubrestapi.model.GHRulesetEnforcement;
import org.jboss.pnc.reqour.service.githubrestapi.model.GHRulesetRule;
import org.jboss.pnc.reqour.service.githubrestapi.model.GHRulesetSourceType;
import org.jboss.pnc.reqour.service.githubrestapi.model.GHRulesetTarget;
import org.jboss.pnc.reqour.service.scmcreation.GitHubApiService;

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

        public static GHRuleset TAG_PROTECTION_RULESET = GHRuleset.builder()
                .id(42)
                .name("test-tag-protection")
                .enforcement(GHRulesetEnforcement.ACTIVE)
                .target(GHRulesetTarget.TAG)
                .sourceType(GHRulesetSourceType.ORGANIZATION)
                .source(InternalSCM.INTERNAL_ORGANIZATION_NAME)
                .conditions(
                        GHRulesetCondition.builder()
                                .repositoryName(
                                        GHRulesetCondition.ConditionRepositoryName.builder()
                                                .include(List.of(GitHubApiService.ALL_REPOSITORIES_PATTERN))
                                                .exclude(Collections.emptyList())
                                                .build())
                                .refName(
                                        GHRulesetCondition.ConditionRefName.builder()
                                                .include(List.of("refs/tags/*"))
                                                .exclude(Collections.emptyList())
                                                .build())
                                .build())
                .rules(
                        List.of(
                                GHRulesetRule.of(GHRulesetRule.GHRulesetRuleType.DELETION),
                                GHRulesetRule.of(GHRulesetRule.GHRulesetRuleType.REQUIRED_LINEAR_HISTORY),
                                GHRulesetRule.of(GHRulesetRule.GHRulesetRuleType.NON_FAST_FORWARD)))
                .build();

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
        public static final String INTERNAL_ORGANIZATION_NAME = "test-organization";
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
}
