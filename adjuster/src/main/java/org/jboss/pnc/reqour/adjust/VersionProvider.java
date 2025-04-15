/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.pnc.reqour.BuildInformationConstants;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;
import picocli.CommandLine;

import java.util.Arrays;

@ApplicationScoped
public class VersionProvider implements CommandLine.IVersionProvider {

    @Inject
    @UserLogger
    Logger userLogger;

    @Startup
    void printVersion() {
        Arrays.stream(getVersion()).forEach(userLogger::info);
    }

    @Override
    public String[] getVersion() {
        return new String[] { "Reqour Adjuster", printVersionAttribute("Version", BuildInformationConstants.VERSION),
                printVersionAttribute("Commit hash", BuildInformationConstants.COMMIT_HASH),
                printVersionAttribute("Build time", BuildInformationConstants.BUILD_TIME) };
    }

    private static String printVersionAttribute(String name, String value) {
        return String.format("\t%s: \t%s", name, value);
    }
}
