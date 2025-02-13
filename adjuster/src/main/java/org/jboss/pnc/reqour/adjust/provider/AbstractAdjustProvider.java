/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfig;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.slf4j.Logger;

import java.util.List;

/**
 * Common parent for all the concrete implementations of {@link AdjustProvider}.
 */
@Slf4j
public abstract class AbstractAdjustProvider<T extends CommonManipulatorConfig> implements AdjustProvider {

    protected T config;
    protected final ObjectMapper objectMapper;
    protected final ProcessExecutor processExecutor;
    protected final Logger userLogger;

    public AbstractAdjustProvider(ObjectMapper objectMapper, ProcessExecutor processExecutor, Logger userLogger) {
        this.objectMapper = objectMapper;
        this.processExecutor = processExecutor;
        this.userLogger = userLogger;
    }

    @Override
    public ManipulatorResult adjust(AdjustRequest adjustRequest) {
        callAdjust();
        userLogger.info("Gonna parse manipulator result");
        var manipulatorResult = obtainManipulatorResult();
        userLogger.info("Parsed manipulator's result is: {}", manipulatorResult);
        return manipulatorResult;
    }

    private void callAdjust() {
        List<String> preparedCommand = prepareCommand();
        userLogger.info("Prepared command to be executed is: {}", preparedCommand);
        int manipulatorExitCode = processExecutor.execute(
                ProcessContext.defaultBuilderWithWorkdir(config.getWorkdir())
                        .stdoutConsumer(this::consumeLogLine)
                        .command(preparedCommand)
                        .build());
        if (manipulatorExitCode == 0) {
            userLogger.info("Manipulator subprocess ended successfully!");
        } else {
            userLogger.warn("Manipulator subprocess ended with failure!");
        }
    }

    private void consumeLogLine(String line) {
        userLogger.info(line);
    }

    /**
     * Prepare the command which invokes the manipulator with all the necessary options parsed from all the config
     * sources, e.g. {@link AdjustRequest} and {@link org.jboss.pnc.reqour.adjust.config.AdjustConfig}.
     */
    abstract List<String> prepareCommand();

    /**
     * Obtain the result from the manipulator.<br/>
     * Note: {@link ManipulatorResult} is the common result format returned into the BPM. The usual way of obtaining
     * this result is by parsing the files where the manipulator stores its results.
     */
    abstract ManipulatorResult obtainManipulatorResult();
}
