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

/**
 * Model for representing GitHub's ruleset. For further details, see
 * <a href="https://docs.github.com/en/rest/orgs/rules#get-an-organization-repository-ruleset">official GitHub docs</a>.
 */
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Value
public class GHRuleset {

    Integer id;

    String name;

    GHRulesetTarget target;

    GHRulesetSourceType sourceType;

    String source;

    GHRulesetEnforcement enforcement;

    GHRulesetCondition conditions;

    List<GHRulesetRule> rules;
}
