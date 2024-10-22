/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfig;
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
    protected List<String> preparedCommand;
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
        // TODO[NCL-8829]: MDC -- BEGIN ALIGNMENT_ADJUST
        String manipulatorOutput = callAdjust();
        log.debug("Manipulator stdout: {}", manipulatorOutput);
        ManipulatorResult manipulatorResult = obtainManipulatorResult();
        log.debug("Parsed adjust response: {}", manipulatorResult);
        AdjustmentPushResult adjustmentPushResult = adjustmentPusher.pushAdjustmentChanges(
                config.getWorkdir(),
                manipulatorResult.getVersioningState().getExecutionRootModified().getVersion(),
                getTagMessage(adjustRequest.getRef(), adjustRequest.getBuildType()));
        return new AdjustmentResult(manipulatorResult, adjustmentPushResult);
        // TODO[NCL-8829]: MDC -- END ALIGNMENT_ADJUST
    }

    protected void validateConfigAndPrepareCommand() {
        if (ConfigProvider.getConfig().getValue("reqour-adjuster.adjust.validate", Boolean.class)) {
            validateConfig();
        }
        log.debug("Config was successfully initialized and validated: {}", config);

        preparedCommand = prepareCommand();
        log.debug("Prepared command is: {}", preparedCommand);
    }

    private String callAdjust() {
        return processExecutor
                .stdout(ProcessContext.defaultBuilderWithWorkdir(config.getWorkdir()).command(preparedCommand));
    }

    private String getTagMessage(String originalReference, BuildType adjustType) {
        return String.format(
                "Tag automatically generated from Reqour\n" + "Original Reference: %s\n" + "Adjust Type: %s",
                originalReference,
                adjustType);
    }

    /**
     * Validate the provided configuration.<br/>
     * Typically, checks whether the provided paths, e.g. CLI jar path, exist in the running environment.
     */
    abstract void validateConfig();

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
