/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import java.io.InputStream;
import java.util.Optional;

import org.jboss.pnc.common.scm.diff.UnifiedDiff;

/**
 * Read-only access to one repository through its git provider's REST API.
 */
interface ProviderContentClient {

    /** The tar.gz archive of the revision; empty when the revision does not exist. */
    Optional<InputStream> archive(String revision);

    /** Whether {@code base} is an ancestor of {@code head}; empty when one of them does not exist. */
    Optional<Boolean> isAncestor(String base, String head);

    /** The complete diff from {@code base} to {@code head} (their trees, like {@code git diff base head}). */
    UnifiedDiff compare(String base, String head);

    /** The complete diff of the commit {@code head} against its first parent; empty when it does not exist. */
    Optional<UnifiedDiff> commitDiff(String head);
}
