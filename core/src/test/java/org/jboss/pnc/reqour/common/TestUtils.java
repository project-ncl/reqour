/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common;

import java.net.URI;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.CancelRequest;
import org.jboss.pnc.api.reqour.dto.InternalGitRepositoryUrl;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;

public class TestUtils {

    public static TranslateResponse createTranslateResponseFromExternalUrl(String externalUrl, String internalUrl) {
        return TranslateResponse.builder().externalUrl(externalUrl).internalUrl(internalUrl).build();
    }

    public static TranslateRequest createTranslateRequestFromExternalUrl(String externalUrl) {
        return TranslateRequest.builder().externalUrl(externalUrl).build();
    }

    public static RepositoryCloneRequest createRepositoryCloneRequest(
            String originRepoUrl,
            String targetRepoUrl,
            String callbackUrl,
            String taskId) {
        return RepositoryCloneRequest.builder()
                .originRepoUrl(originRepoUrl)
                .targetRepoUrl(targetRepoUrl)
                .taskId(taskId)
                .callback(Request.builder().method(Request.Method.POST).uri(URI.create(callbackUrl)).build())
                .build();
    }

    public static RepositoryCloneResponse createRepositoryCloneResponse(
            String originRepoUrl,
            String targetRepoUrl,
            ResultStatus status,
            String taskId) {
        return RepositoryCloneResponse.builder()
                .originRepoUrl(originRepoUrl)
                .targetRepoUrl(targetRepoUrl)
                .callback(ReqourCallback.builder().status(status).id(taskId).build())
                .build();
    }

    public static InternalSCMCreationResponse newlyCreatedSuccess(String projectPath, String taskId) {
        return createResponse(projectPath, taskId, InternalSCMCreationStatus.SUCCESS_CREATED, ResultStatus.SUCCESS);
    }

    public static InternalSCMCreationResponse alreadyExistsSuccess(String projectPath, String taskId) {
        return createResponse(
                projectPath,
                taskId,
                InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS,
                ResultStatus.SUCCESS);
    }

    public static InternalSCMCreationResponse failed(String projectPath, String taskId) {
        return createResponse(projectPath, taskId, InternalSCMCreationStatus.FAILED, ResultStatus.FAILED);
    }

    private static InternalSCMCreationResponse createResponse(
            String projectPath,
            String taskId,
            InternalSCMCreationStatus creationStatus,
            ResultStatus operationStatus) {
        return InternalSCMCreationResponse.builder()
                .readonlyUrl("http://localhost/" + projectPath + ".git")
                .readwriteUrl("git@localhost:" + projectPath + ".git")
                .status(creationStatus)
                .callback(ReqourCallback.builder().id(taskId).status(operationStatus).build())
                .build();
    }

    public static InternalSCMCreationRequest createInternalSCMRepoCreationRequest(
            String projectPath,
            String taskId,
            String callbackPath) {
        return InternalSCMCreationRequest.builder()
                .project(projectPath)
                .taskId(taskId)
                .callback(
                        Request.builder()
                                .method(Request.Method.POST)
                                .uri(URI.create(getWiremockBaseUrl() + callbackPath))
                                .build())
                .build();
    }

    public static AdjustRequest createAdjustRequest() {
        return AdjustRequest.builder()
                .taskId(TestDataSupplier.TASK_ID)
                .buildType(BuildType.MVN)
                .callback(
                        Request.builder()
                                .method(Request.Method.POST)
                                .uri(URI.create(TestUtils.getWiremockBaseUrl() + TestDataSupplier.CALLBACK_PATH))
                                .build())
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readonlyUrl("https://github.com/project/repo")
                                .readwriteUrl("git@github.com:project/repo")
                                .build())
                .originRepoUrl("https://github.com/project/repo")
                .build();
    }

    public static CancelRequest createCancelRequest(String taskId, String callbackPath) {
        return CancelRequest.builder()
                .taskId(taskId)
                .callback(
                        Request.builder()
                                .method(Request.Method.POST)
                                .uri(URI.create(getWiremockBaseUrl() + callbackPath))
                                .build())
                .build();
    }

    public static String getWiremockBaseUrl() {
        return ConfigProvider.getConfig().getValue("wiremock.base-url", String.class);
    }
}
