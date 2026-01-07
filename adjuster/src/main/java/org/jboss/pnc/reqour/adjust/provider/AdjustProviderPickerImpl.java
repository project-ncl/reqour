/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.service.CommonManipulatorResultExtractor;
import org.jboss.pnc.reqour.adjust.service.RootGavExtractor;
import org.jboss.pnc.reqour.adjust.utils.CommonUtils;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.config.adjuster.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class AdjustProviderPickerImpl implements AdjustProviderPicker {

    private final ReqourAdjusterConfig config;
    private final ObjectMapper objectMapper;
    private final ProcessExecutor processExecutor;
    private final CommonManipulatorResultExtractor adjustResultExtractor;
    private final RootGavExtractor rootGavExtractor;
    private final Path workdir = CommonUtils.getAdjustDir();
    private final Logger userLogger;

    @Inject
    public AdjustProviderPickerImpl(
            ReqourAdjusterConfig config,
            ObjectMapper objectMapper,
            ProcessExecutor processExecutor,
            CommonManipulatorResultExtractor adjustResultExtractor,
            RootGavExtractor rootGavExtractor,
            @UserLogger Logger userLogger) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.processExecutor = processExecutor;
        this.adjustResultExtractor = adjustResultExtractor;
        this.rootGavExtractor = rootGavExtractor;
        this.userLogger = userLogger;
    }

    @Override
    public AdjustProvider pickAdjustProvider(AdjustRequest adjustRequest) {
        return switch (adjustRequest.getBuildType()) {
            case MVN, MVN_RPM -> new MvnProvider(
                    config.alignment(),
                    adjustRequest,
                    workdir,
                    objectMapper,
                    processExecutor,
                    adjustResultExtractor,
                    rootGavExtractor,
                    userLogger);
            case GRADLE -> new GradleProvider(
                    config.alignment(),
                    adjustRequest,
                    workdir,
                    objectMapper,
                    processExecutor,
                    adjustResultExtractor,
                    userLogger);
            case NPM ->
                new NpmProvider(config.alignment(), adjustRequest, workdir, objectMapper, processExecutor, userLogger);
            case SBT ->
                new SbtProvider(config.alignment(), adjustRequest, workdir, objectMapper, processExecutor, userLogger);
        };
    }
}
