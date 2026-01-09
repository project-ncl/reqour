/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import java.util.List;

import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.reqour.adjust.config.AlignmentConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfig;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

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
                ProcessContext.withWorkdirAndConsumers(config.getWorkdir(), userLogger::info, userLogger::warn)
                        .command(preparedCommand)
                        .build());
        if (manipulatorExitCode == 0) {
            userLogger.info("Manipulator subprocess ended successfully!");
        } else {
            userLogger.warn("Manipulator subprocess ended with failure!");
            throw new AdjusterException("Manipulator subprocess ended with non-zero exit code");
        }
    }

    /**
     * Prepare the command which invokes the manipulator with all the necessary options parsed from all the config
     * sources, e.g. {@link AdjustRequest} and {@link AlignmentConfig}.
     */
    abstract List<String> prepareCommand();

    /**
     * Several alignment parameters need to be overridden based on some sources (e.g. adjust request and adjust config).
     * This abstract method enforces the behaviour for every manipulator.<br/>
     * This method is expected to be called within {@link this#prepareCommand()} method.
     */
    abstract List<String> computeAlignmentParametersOverrides();

    /**
     * Obtain the result from the manipulator.<br/>
     * Note: {@link ManipulatorResult} is the common result format returned into the BPM. The usual way of obtaining
     * this result is by parsing the files where the manipulator stores its results.
     */
    abstract ManipulatorResult obtainManipulatorResult();
}
