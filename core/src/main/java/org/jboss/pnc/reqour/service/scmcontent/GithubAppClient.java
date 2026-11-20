/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import java.io.IOException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.reqour.common.exceptions.GithubAppNoInstallationException;
import org.jboss.pnc.reqour.common.exceptions.GithubAppNotFoundException;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GitHub;

import io.quarkiverse.githubapp.GitHubClientProvider;
import io.quarkiverse.githubapp.InstallationTokenProvider;

/**
 * Client that deals with Github app setup. We can use it to access repositories and APIs.
 * This is one of the ways to communicate with Github, the other being PAT tokens and OAuth tokens.
 * <br/>
 * We need to define in the properties:
 * - quarkus.github-app.app-id
 * - quarkus.github-app.private-key
 * - (optional) quarkus.github-app.instance-endpoint = https://api.github.com
 */
@ApplicationScoped
public class GithubAppClient {

    @Inject
    InstallationTokenProvider provider;

    @Inject
    GitHubClientProvider gitHubClientProvider;

    /**
     * Grab the installationToken from Github to use for git cloning etc.
     * <br/>
     * A Github app can be installed to 1 or more organizations. Each installation has an installation id.
     * From the githubAppId, the private-key of that github-app, a JWT signed token is generated. The JWT signed token
     * and installation id are then exchanged with Github to get an installation token, which is valid for 1 hour.
     * <br/>
     * Note that this method *assumes* that we will install the github app only once!
     * <br/>
     * To use it for git cloning: git clone @link{https://x-access-token:INSTALLATION_TOKEN@github-url/org/project.git}
     *
     * @return installation token
     */
    public String getInstallationToken() {

        GitHub appClient = gitHubClientProvider.getApplicationClient();
        GHApp app = null;
        try {
            app = appClient.getApp();
        } catch (IOException e) {
            throw new GithubAppNotFoundException(e);
        }

        for (GHAppInstallation installation : app.listInstallations()) {
            // return first installation it'll find
            long installationId = installation.getId();
            return provider.getInstallationToken(installationId).token();
        }
        throw new GithubAppNoInstallationException("No installation present!");
    }
}