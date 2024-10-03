/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * The entrypoint of the reqour adjuster.
 */
@TopCommand
@CommandLine.Command(
        name = "adjust",
        description = "Execute the alignment with the corresponding built tool and manipulator",
        mixinStandardHelpOptions = true)
@Slf4j
public class App implements Callable<Object> {

    @Override
    public Object call() throws Exception {
        log.debug("App is running");
        return null;
    }
}
