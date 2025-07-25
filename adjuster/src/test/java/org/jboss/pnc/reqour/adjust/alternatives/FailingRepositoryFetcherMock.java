/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.alternatives;

import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.model.CloningResult;
import org.jboss.pnc.reqour.adjust.service.RepositoryFetcher;
import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

@Alternative
@ApplicationScoped
public class FailingRepositoryFetcherMock implements RepositoryFetcher {

    @Inject
    @UserLogger
    Logger userLogger;

    @Override
    public CloningResult cloneRepository(AdjustRequest adjustRequest, Path workdir) {
        userLogger.info("Cloning a repository");
        throw new GitException("Oops, git exception");
    }
}
