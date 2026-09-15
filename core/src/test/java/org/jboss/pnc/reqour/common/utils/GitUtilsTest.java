/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class GitUtilsTest {

    @Test
    void submoduleUpdateInit_rewritesInsecureGithubProtocol() {
        // Some repositories (e.g. okhttp's hpack-test-case submodule) declare their submodules with the unauthenticated
        // git://github.com/ protocol, which GitHub permanently disabled in 2022 (and which is commonly firewall-blocked)
        // -- cloning such a submodule hangs until it times out. 'git submodule update --init' must therefore rewrite
        // git://github.com/ to https://github.com/.
        //
        // The rewrite has to be passed as '-c' on the command itself (rather than via a separate 'git config', which
        // sets the surrounding repository's *local* config): the fresh 'git clone' subprocess that git spawns per
        // submodule does not read that local config, but '-c' propagates to it through GIT_CONFIG_PARAMETERS. The '-c'
        // flag also has to precede the 'submodule' subcommand to be recognized.
        List<String> command = GitUtils.submoduleUpdateInit();

        assertThat(command).containsExactly(
                "git",
                "-c",
                "url.https://github.com/.insteadOf=git://github.com/",
                "submodule",
                "update",
                "--init");
    }
}
