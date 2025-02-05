/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

/**
 * Result of committing the adjustment once the manipulation phase ends.
 * @param commit ID of the commit which contains alignment changes
 * @param tag generated tag which corresponds to the {@link this#commit}
 */
public record AdjustmentPushResult(String commit, String tag) {
}
