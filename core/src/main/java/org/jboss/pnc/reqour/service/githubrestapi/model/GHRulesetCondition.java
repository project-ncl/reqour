/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.githubrestapi.model;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GHRulesetCondition {

    ConditionRefName refName;

    ConditionRepositoryName repositoryName;

    @Value
    @Builder
    @Jacksonized
    public static class ConditionRefName {

        List<String> include;
        List<String> exclude;

        @Override
        public String toString() {
            return "ConditionRefName(include=" + include + ", exclude=" + exclude + ")";
        }
    }

    @Value
    @Builder
    @Jacksonized
    public static class ConditionRepositoryName {

        List<String> include;
        List<String> exclude;

        @Override
        public String toString() {
            return "ConditionRepositoryName(include=" + include + ", exclude=" + exclude + ")";
        }
    }

    @Override
    public String toString() {
        return "GHRulesetCondition(refName=" + refName + ", repositoryName=" + repositoryName + ")";
    }
}
