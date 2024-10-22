/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import org.jboss.pnc.reqour.adjust.exception.InvalidConfigException;

import java.nio.file.Files;
import java.nio.file.Path;

public class InvalidConfigUtils {

    public static void validateResourceAtPathExists(Path path, String errorMessageTemplate) {
        if (!Files.exists(path)) {
            throw new InvalidConfigException(String.format(errorMessageTemplate, path));
        }
    }
}
