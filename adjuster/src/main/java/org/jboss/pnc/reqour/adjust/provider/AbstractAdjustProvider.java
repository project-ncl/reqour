/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.common.log.ProcessStageUtils;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfig;
import org.jboss.pnc.reqour.adjust.enums.AdjustProcessStage;
import org.jboss.pnc.reqour.adjust.model.AdjustmentPushResult;
import org.jboss.pnc.reqour.adjust.model.AdjustmentResult;
import org.jboss.pnc.reqour.adjust.service.AdjustmentPusher;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.model.ProcessContext;

import java.util.List;

/**
 * Common parent for all the concrete implementations of {@link AdjustProvider}.
 */
@Slf4j
public abstract class AbstractAdjustProvider<T extends CommonManipulatorConfig> implements AdjustProvider {

    private final AdjustmentPusher adjustmentPusher;
    protected T config;
    protected final ObjectMapper objectMapper;
    protected final ProcessExecutor processExecutor;

    public AbstractAdjustProvider(
            ObjectMapper objectMapper,
            ProcessExecutor processExecutor,
            AdjustmentPusher adjustmentPusher) {
        this.objectMapper = objectMapper;
        this.processExecutor = processExecutor;
        this.adjustmentPusher = adjustmentPusher;
    }

    @Override
    public AdjustmentResult adjust(AdjustRequest adjustRequest) {
        ProcessStageUtils.logProcessStageBegin(AdjustProcessStage.ALIGNMENT.name());

        callAdjust();
        ManipulatorResult manipulatorResult = obtainManipulatorResult();
        log.debug("Parsed adjust response: {}", manipulatorResult);
        AdjustmentPushResult adjustmentPushResult = adjustmentPusher.pushAdjustmentChanges(
                config.getWorkdir(),
                manipulatorResult.getVersioningState().getExecutionRootVersion(),
                getTagMessage(adjustRequest.getRef(), adjustRequest.getBuildType()));

        ProcessStageUtils.logProcessStageEnd(AdjustProcessStage.ALIGNMENT.name());
        return new AdjustmentResult(manipulatorResult, adjustmentPushResult);
    }

    private void callAdjust() {
        List<String> preparedCommand = prepareCommand();
        log.debug("Prepared command is: {}", preparedCommand);
        processExecutor.execute(
                ProcessContext.defaultBuilderWithWorkdir(config.getWorkdir())
                        .stdoutConsumer(this::consumeLogLine)
                        .command(preparedCommand)
                        .build());
    }

    private void consumeLogLine(String line) {
        log.info(line);
        // TODO[NCL-8813]: Handle logging (sending to bifrost, configuring kafka logging)
    }

    private String getTagMessage(String originalReference, BuildType adjustType) {
        return String.format(
                "Tag automatically generated from Reqour\n" + "Original Reference: %s\n" + "Adjust Type: %s",
                originalReference,
                adjustType);
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
