/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.AdjustResponse;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfig;

import java.nio.file.Path;
import java.util.List;

/**
 * TODO: Write Javadoc once process executor is used in {@link this#adjust()}.
 */
@Slf4j
public abstract class AbstractAdjustProvider<T extends CommonManipulatorConfig> implements AdjustProvider {

    protected List<String> preparedCommand;
    protected T config;

    public AbstractAdjustProvider(AdjustConfig adjustConfig, AdjustRequest adjustRequest, Path workdir) {
        init(adjustConfig, adjustRequest, workdir);
        validateConfig();
        preparedCommand = prepareCommand();
        log.debug("Generated command: {}", preparedCommand);
    }

    abstract void init(AdjustConfig adjustConfig, AdjustRequest adjustRequest, Path workdir);

    abstract void validateConfig();

    /**
     * Prepare the shell command based on the config.
     *
     * @return array representing bash command
     */
    abstract List<String> prepareCommand();

    @Override
    public AdjustResponse adjust() {
        return null;
    }
}
