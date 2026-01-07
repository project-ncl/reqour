/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime.api.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GHRulesetSourceType {

    @JsonProperty("Repository") REPOSITORY,

    @JsonProperty("Organization") ORGANIZATION,

    @JsonProperty("Enterprise") ENTERPRISE,
    ;
}
