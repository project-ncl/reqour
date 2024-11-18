/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.openshift;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.rest.config.ReqourRestConfig;

import java.util.List;

/**
 * Controller of the reqour adjuster job, which is used to create and destroy these jobs at the configured OpenShift
 * cluster.
 */
@ApplicationScoped
@Slf4j
public class OpenShiftAdjusterPodController {

    private final ReqourRestConfig config;
    private final ManagedExecutor executor;
    private final OpenShiftClient openShiftClient;
    private final JobDefinitionCreator jobDefinitionCreator;

    @Inject
    public OpenShiftAdjusterPodController(
            ReqourRestConfig config,
            ManagedExecutor executor,
            OpenShiftClient openShiftClient,
            JobDefinitionCreator jobDefinitionCreator) {
        this.config = config;
        this.executor = executor;
        this.openShiftClient = openShiftClient;
        this.jobDefinitionCreator = jobDefinitionCreator;
    }

    public void createAdjusterJob(AdjustRequest adjustRequest) {
        Job adjusterJob = jobDefinitionCreator
                .getAdjusterJobDefinition(adjustRequest, getJobName(adjustRequest.getTaskId()));
        Failsafe.with(getOpenShiftRetryPolicy()).with(executor).getAsync(() -> {
            log.debug("Creating reqour adjuster job in the cluster");
            Job job = openShiftClient.resource(adjusterJob).create();
            log.debug("Job '{}' was successfully created", job.getMetadata().getName());
            return job;
        });
    }

    public void destroyAdjusterJob(String taskId) {
        Job job = openShiftClient.resources(Job.class).withName(getJobName(taskId)).item();
        Failsafe.with(getOpenShiftRetryPolicy()).with(executor).runAsync(() -> {
            log.debug("Removing reqour adjuster job corresponding to task ID '{}' from the cluster", taskId);
            List<StatusDetails> statusDetails = openShiftClient.resource(job).delete();
            log.debug("{}", statusDetails);
        });
    }

    private RetryPolicy<Object> getOpenShiftRetryPolicy() {
        ReqourRestConfig.RetryConfig openShiftRetryConfig = config.openshiftRetryConfig();

        return RetryPolicy.builder()
                .withBackoff(openShiftRetryConfig.backoffInitialDelay(), openShiftRetryConfig.backoffMaxDelay())
                .withMaxRetries(openShiftRetryConfig.maxRetries())
                .withMaxDuration(openShiftRetryConfig.maxDuration())
                .onSuccess((e) -> log.debug("Request successfully sent"))
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
