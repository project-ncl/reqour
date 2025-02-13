/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import org.jboss.pnc.api.reqour.dto.AdjustRequest;

/**
 * Picker of the correct {@link AdjustProvider} based on the given {@link AdjustRequest}.
 */
public interface AdjustProviderPicker {

    AdjustProvider pickAdjustProvider(AdjustRequest adjustRequest);
}
