/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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

    @Test
    void addCredentialsToUrl_https_injectsUserAndToken() {
        assertThat(URLUtils.addCredentialsToUrl("https://gitlab.com/lw/lw-builds/foo.git", "oauth2", "tok"))
                .isEqualTo("https://oauth2:tok@gitlab.com/lw/lw-builds/foo.git");
    }

    @Test
    void addCredentialsToUrl_blankUsername_defaultsToOauth2() {
        assertThat(URLUtils.addCredentialsToUrl("https://gitlab.com/lw/foo.git", "  ", "tok"))
                .isEqualTo("https://oauth2:tok@gitlab.com/lw/foo.git");
    }

    @Test
    void addCredentialsToUrl_replacesExistingUserinfo() {
        assertThat(URLUtils.addCredentialsToUrl("https://old@gitlab.com/lw/foo.git", "bot", "tok"))
                .isEqualTo("https://bot:tok@gitlab.com/lw/foo.git");
    }

    @Test
    void addCredentialsToUrl_nonHttpOrMissingToken_returnsUnchanged() {
        assertThat(URLUtils.addCredentialsToUrl("git@gitlab.com:lw/foo.git", "bot", "tok"))
                .isEqualTo("git@gitlab.com:lw/foo.git");
        assertThat(URLUtils.addCredentialsToUrl("https://gitlab.com/lw/foo.git", "bot", null))
                .isEqualTo("https://gitlab.com/lw/foo.git");
    }

    @Test
    void normalizeHostAndPath_variousForms_normalizeToHostSlashPath() {
        String expected = "gitlab.com/lw/lw-builds/foo";
        assertThat(URLUtils.normalizeHostAndPath("https://gitlab.com/lw/lw-builds/foo.git")).isEqualTo(expected);
        assertThat(URLUtils.normalizeHostAndPath("git+ssh://git@gitlab.com/lw/lw-builds/foo.git")).isEqualTo(expected);
        assertThat(URLUtils.normalizeHostAndPath("git@gitlab.com:lw/lw-builds/foo.git")).isEqualTo(expected);
        assertThat(URLUtils.normalizeHostAndPath("https://gitlab.com:443/lw/lw-builds/foo/")).isEqualTo(expected);
    }

    @Test
    void convertToHttps_scpAndSshForms_becomeHttps() {
        assertThat(URLUtils.convertToHttps("git@gitlab.com:lw/lw-builds/repo.git"))
                .isEqualTo("https://gitlab.com/lw/lw-builds/repo.git");
        assertThat(URLUtils.convertToHttps("git+ssh://git@gitlab.com/lw/lw-builds/repo.git"))
                .isEqualTo("https://gitlab.com/lw/lw-builds/repo.git");
        assertThat(URLUtils.convertToHttps("ssh://git@gitlab.com/lw/repo.git"))
                .isEqualTo("https://gitlab.com/lw/repo.git");
        // already https / http -> unchanged
        assertThat(URLUtils.convertToHttps("https://gitlab.com/lw/repo.git"))
                .isEqualTo("https://gitlab.com/lw/repo.git");
    }
}
