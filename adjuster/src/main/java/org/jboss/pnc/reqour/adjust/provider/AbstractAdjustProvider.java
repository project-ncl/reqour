/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.reqour.dto.AdjustResponse;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfig;

import java.util.List;

/**
 * TODO: Write Javadoc once process executor is used in {@link this#adjust()}.
 */
@Slf4j
public abstract class AbstractAdjustProvider<T extends CommonManipulatorConfig> implements AdjustProvider {

    protected List<String> preparedCommand;
    protected T config;

    @Override
    public AdjustResponse adjust() {
        // TODO
        // Will be probably as easy as running the command via process executor, but cannot be 100% sure that it's
        // actually correct, since don't have the reqour-adjuster containerfile yet.
        return null;
    }

    void validateConfigAndPrepareCommand() {
        if (ConfigProvider.getConfig().getValue("reqour-adjuster.adjust.validate", Boolean.class)) {
            validateConfig();
        }
        log.debug("Config was successfully initialized and validated: {}", config);

        preparedCommand = prepareCommand();
        log.debug("Prepared command is: {}", preparedCommand);
    }

    abstract void validateConfig();

    abstract List<String> prepareCommand();
}
