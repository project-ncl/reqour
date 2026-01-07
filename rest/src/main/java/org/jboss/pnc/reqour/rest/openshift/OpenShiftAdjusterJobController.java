/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.openshift;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.config.rest.ReqourRestConfig;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.openshift.client.OpenShiftClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller of the reqour adjuster job, which is used to create and destroy these jobs at the configured OpenShift
 * cluster.
 */
@ApplicationScoped
@Slf4j
public class OpenShiftAdjusterJobController {

    private final ReqourRestConfig config;
    private final ManagedExecutor executor;
    private final OpenShiftClient openShiftClient;
    private final JobDefinitionCreator jobDefinitionCreator;
    private final Logger userLogger;

    @Inject
    public OpenShiftAdjusterJobController(
            ReqourRestConfig config,
            ManagedExecutor executor,
            OpenShiftClient openShiftClient,
            JobDefinitionCreator jobDefinitionCreator,
            @UserLogger Logger userLogger) {
        this.config = config;
        this.executor = executor;
        this.openShiftClient = openShiftClient;
        this.jobDefinitionCreator = jobDefinitionCreator;
        this.userLogger = userLogger;
    }

    public void createAdjusterJob(AdjustRequest adjustRequest) {
        String jobName = getJobName(adjustRequest.getTaskId());
        Job adjusterJob = jobDefinitionCreator.getAdjusterJobDefinition(adjustRequest, jobName);
        Failsafe.with(getOpenShiftRetryPolicy()).with(executor).getAsync(() -> {
            userLogger.info("Creating reqour adjuster job '{}' in the cluster", jobName);
            return openShiftClient.resource(adjusterJob).create();
        });
    }

    public ResultStatus destroyAdjusterJob(String taskId) {
        Job job = openShiftClient.batch().v1().jobs().withName(getJobName(taskId)).get();
        if (job == null) {
            log.warn("Job corresponding to task ID '{}' was not found", taskId);
            return ResultStatus.FAILED;
        }

        Failsafe.with(getOpenShiftRetryPolicy()).with(executor).runAsync(() -> {
            log.debug("Removing reqour adjuster job corresponding to task ID '{}' from the cluster", taskId);
            List<StatusDetails> statusDetails = openShiftClient.resource(job).delete();
            if (statusDetails.size() == 1) {
                log.debug("Job with name '{}' successfully deleted", statusDetails.getFirst().getName());
            }
        });
        return ResultStatus.CANCELLED;
    }

    private RetryPolicy<Object> getOpenShiftRetryPolicy() {
        ReqourRestConfig.RetryConfig openShiftRetryConfig = config.openshiftRetryConfig();

        return RetryPolicy.builder()
                .withBackoff(openShiftRetryConfig.backoffInitialDelay(), openShiftRetryConfig.backoffMaxDelay())
                .withMaxRetries(openShiftRetryConfig.maxRetries())
                .withMaxDuration(openShiftRetryConfig.maxDuration())
                .onSuccess((e) -> userLogger.info("Request successfully sent"))
                .onRetry(
                        (e) -> log.debug(
                                "Retrying (attempt #{}), last exception was: {}",
                                e.getAttemptCount(),
                                e.getLastException().getMessage()))
                .onFailure((e) -> log.warn("Request couldn't be sent.", e.getException()))
                .build();
    }

    private String getJobName(String taskId) {
        String lowerTaskId = taskId.toLowerCase();
        String adjustedTaskId = lowerTaskId.replaceAll("[^a-z0-9]", "x");

        if (!adjustedTaskId.equals(lowerTaskId)) {
            log.warn("task id '{}' contains invalid characters, converted to '{}'", taskId, adjustedTaskId);
        }

        String jobName = String.format("reqour-adjuster-%s", adjustedTaskId);
        log.info("For the task ID '{}' using job name '{}'", taskId, jobName);
        return jobName;
    }
}
