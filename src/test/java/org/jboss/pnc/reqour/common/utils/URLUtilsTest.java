/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2024-2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.reqour.common.utils;

import io.quarkus.test.junit.TestProfile;
import org.jboss.pnc.reqour.profile.UtilsProfile;
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
