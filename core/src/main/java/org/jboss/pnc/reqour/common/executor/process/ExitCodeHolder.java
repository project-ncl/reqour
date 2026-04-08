/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.executor.process;

import lombok.Getter;
import lombok.Setter;

/**
 * Simple class holding the exit code of a process.<br/>
 * The wrapper around this int (exit code) is created, so that we can use it in lambdas (where referenced variables have
 * to be either final or effectively final, which wouldn't be possible with a regular int).
 */
@Getter
@Setter
public class ExitCodeHolder {

    private int exitCode;
}
