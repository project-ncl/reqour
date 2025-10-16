/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.profile;

import java.util.Map;
import java.util.Set;

public class TranslationWithGithubProviderProfile extends CommonTestProfile {

    @Override
    public Set<String> tags() {
        return Set.of(TestTag.TRANSLATION.name());
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("reqour.git.git-providers.active", "github");
    }
}
