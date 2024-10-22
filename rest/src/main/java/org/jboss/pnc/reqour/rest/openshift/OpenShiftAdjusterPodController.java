/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.openshift;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.common.exceptions.UnsupportedTaskIdFormatException;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.rest.config.ReqourRestConfig;

import java.net.http.HttpResponse;

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
        Failsafe.with(getOpenShiftRetryPolicy()).with(executor).runAsync(() -> {
            log.debug("Creating reqour adjuster pod in the cluster");
            openShiftClient.resource(adjusterPod).create();
        });
    }

    public void destroyAdjusterPod(String taskId) {
        Pod adjusterPod = openShiftClient.pods().withName(getPodName(taskId)).get();
        Failsafe.with(getOpenShiftRetryPolicy()).with(executor).runAsync(() -> {
            log.debug("Removing reqour adjuster pod from the cluster");
            openShiftClient.resource(adjusterPod).delete();
        });
    }

    private RetryPolicy<HttpResponse<String>> getOpenShiftRetryPolicy() {
        ReqourRestConfig.RetryConfig openShiftRetryConfig = config.openshiftRetryConfig();

        return RetryPolicy.<HttpResponse<String>> builder()
                .withBackoff(openShiftRetryConfig.backoffInitialDelay(), openShiftRetryConfig.backoffMaxDelay())
                .withMaxRetries(openShiftRetryConfig.maxRetries())
                .withMaxDuration(openShiftRetryConfig.maxDuration())
                .onSuccess((e) -> log.debug("Request successfully sent, response: {}", e.getResult().statusCode()))
                .onRetry(
                        (e) -> log.debug(
                                "Retrying (attempt #{}), last exception was: {}",
                                e.getAttemptCount(),
                                e.getLastException().getMessage()))
                .onFailure((e) -> log.warn("Request couldn't be sent.", e.getException()))
                .build();
    }

    private String getPodName(String taskId) {
        StringBuilder adjustedTaskIdSb = new StringBuilder();
        for (int i = 0; i < taskId.length(); i++) {
            char c = taskId.charAt(i);
            if ('A' <= c && c <= 'Z') {
                adjustedTaskIdSb.append(IOUtils.transformUppercaseCharToLowercase(c));
            } else if ('0' <= c && c <= '9') {
                adjustedTaskIdSb.append(c);
            } else {
                throw new UnsupportedTaskIdFormatException(
                        String.format(
                                "Given task ID '%s' contains invalid character '%s'. Supported characters are: [a-zA-Z0-9]",
                                taskId,
                                c));
            }
        }

        String podName = String.format("reqour-adjuster-%s", adjustedTaskIdSb);
        log.info("For the task ID '{}' using pod name '{}'", taskId, podName);
        return podName;
    }
}
