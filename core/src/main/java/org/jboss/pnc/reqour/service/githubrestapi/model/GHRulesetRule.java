/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.githubrestapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
@Value
public class GHRulesetRule {

    GHRulesetRuleType type;

    public static GHRulesetRule of(GHRulesetRuleType type) {
        return GHRulesetRule.builder()
                .type(type)
                .build();
    }

    public enum GHRulesetRuleType {

        @JsonProperty("creation") CREATION,

        @JsonProperty("update") UPDATE,

        @JsonProperty("deletion") DELETION,

        @JsonProperty("required_linear_history") REQUIRED_LINEAR_HISTORY,

        @JsonProperty("merge_queue") MERGE_QUEUE,

        @JsonProperty("required_deployments") REQUIRED_DEPLOYMENTS,

        @JsonProperty("required_signatures") REQUIRED_SIGNATURES,

        @JsonProperty("pull_request") PULL_REQUEST,

        @JsonProperty("required_status_checks") REQUIRED_STATUS_CHECKS,

        @JsonProperty("non_fast_forward") NON_FAST_FORWARD,

        @JsonProperty("commit_message_pattern") COMMIT_MESSAGE_PATTERN,

        @JsonProperty("commit_author_email_pattern") COMMIT_AUTHOR_EMAIL_PATTERN,

        @JsonProperty("committer_email_pattern") COMMITTER_EMAIL_PATTERN,

        @JsonProperty("branch_name_pattern") BRANCH_NAME_PATTERN,

        @JsonProperty("tag_name_pattern") TAG_NAME_PATTERN,

        @JsonProperty("file_path_restriction") FILE_PATH_RESTRICTION,

        @JsonProperty("max_file_path_length") MAX_FILE_PATH_LENGTH,

        @JsonProperty("file_extension_restriction") FILE_EXTENSION_RESTRICTION,

        @JsonProperty("max_file_size") MAX_FILE_SIZE,

        @JsonProperty("workflows") WORKFLOWS,

        @JsonProperty("code_scanning") CODE_SCANNING,

        @JsonProperty("copilot_code_review") COPILOT_CODE_REVIEW,
        ;
    }

    @Override
    public String toString() {
        return "GHRulesetRule(type=" + type + ")";
    }
}
