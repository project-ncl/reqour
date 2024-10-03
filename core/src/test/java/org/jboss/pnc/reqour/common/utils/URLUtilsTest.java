/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import io.quarkus.test.junit.TestProfile;
import org.jboss.pnc.reqour.common.profile.UtilsProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@TestProfile(UtilsProfile.class)
class URLUtilsTest {

    @Test
    void addUsernameToUrl_scpLike_returnsUrlUnchanged() {
        String url = "git@github.com:foo/bar.git";

        String adjustedUrl = URLUtils.addUsernameToUrl(url, "whatever");

        assertThat(adjustedUrl).isEqualTo(url);
    }

    @Test
    void addUsernameToUrl_nonScpLikeWithoutUser_returnsAdjustedUrl() {
        String adjustedUrl = URLUtils.addUsernameToUrl("https://github.com/foo/bar.git", "user");

        assertThat(adjustedUrl).isEqualTo("https://user@github.com/foo/bar.git");
    }
}
