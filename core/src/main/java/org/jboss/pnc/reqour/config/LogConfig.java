/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

public interface LogConfig {

    FinalLogConfig finalLog();

    UserLogConfig userLog();

    interface UserLogConfig {

        String userLoggerName();
    }

    interface FinalLogConfig {

        BifrostUploaderConfig bifrostUploader();

        String uploaderBaseName();
    }
}
