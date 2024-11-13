/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.openshift;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.rest.config.ReqourRestConfig;

import java.util.List;

/**
 * Controller of the reqour adjuster pod, which is used to create and destroy these pods at the configured OpenShift
 * cluster.
 */
@ApplicationScoped
@Slf4j
public class OpenShiftAdjusterPodController {

    private final ReqourRestConfig config;
    private final ManagedExecutor executor;
    private final OpenShiftClient openShiftClient;
    private final PodDefinitionCreator podDefinitionCreator;

    @Inject
    public OpenShiftAdjusterPodController(
            ReqourRestConfig config,
            ManagedExecutor executor,
            OpenShiftClient openShiftClient,
            PodDefinitionCreator podDefinitionCreator) {
        this.config = config;
        this.executor = executor;
        this.openShiftClient = openShiftClient;
        this.podDefinitionCreator = podDefinitionCreator;
    }

    public void createAdjusterPod(AdjustRequest adjustRequest) {
        Pod adjusterPod = podDefinitionCreator
                .getAdjusterPodDefinition(adjustRequest, getPodName(adjustRequest.getTaskId()));
        Failsafe.with(getOpenShiftRetryPolicy()).with(executor).getAsync(() -> {
            log.debug("Creating reqour adjuster pod in the cluster");
            Pod pod = openShiftClient.resource(adjusterPod).create();
            log.debug("Pod '{}' was successfully created", pod.getMetadata().getName());
            return pod;
        });
    }

    public void destroyAdjusterPod(String taskId) {
        Pod adjusterPod = openShiftClient.pods().withName(getPodName(taskId)).get();
        Failsafe.with(getOpenShiftRetryPolicy()).with(executor).runAsync(() -> {
            log.debug("Removing reqour adjuster pod from the cluster");
            List<StatusDetails> statusDetails = openShiftClient.resource(adjusterPod).delete();
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

    private String getPodName(String taskId) {
        String lowerTaskId = taskId.toLowerCase();
        String adjustedTaskId = lowerTaskId.replaceAll("[^a-z0-9]", "x");

        if (!adjustedTaskId.equals(lowerTaskId)) {
            log.warn("task id '{}' contains invalid characters, converted to '{}'", taskId, adjustedTaskId);
        }

        String podName = String.format("reqour-adjuster-%s", adjustedTaskId);
        log.info("For the task ID '{}' using pod name '{}'", taskId, podName);
        return podName;
    }
}
