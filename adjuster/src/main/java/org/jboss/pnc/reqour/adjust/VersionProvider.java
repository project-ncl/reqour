/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.pnc.reqour.BuildInformationConstants;
import picocli.CommandLine;

@ApplicationScoped
public class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() throws Exception {
        return new String[] { "Reqour Adjuster", printVersionAttribute("Version", BuildInformationConstants.VERSION),
                printVersionAttribute("Commit hash", BuildInformationConstants.COMMIT_HASH),
                printVersionAttribute("Build time", BuildInformationConstants.BUILD_TIME) };
    }

    private static String printVersionAttribute(String name, String value) {
        return String.format("\t%s: \t%s", name, value);
    }
}
