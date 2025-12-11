/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.githubrestapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GHRulesetEnforcement {

    @JsonProperty("disabled") DISABLED,

    @JsonProperty("active") ACTIVE,

    @JsonProperty("evaluate") EVALUATE,
    ;
}
