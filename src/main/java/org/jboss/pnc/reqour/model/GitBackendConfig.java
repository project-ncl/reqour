/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2024-2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.reqour.model;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Builder
public class GitBackendConfig {

    String name;
    String username;
    String url;
    String workspace;
    long workspaceId;
    String readOnlyTemplate;
    String readWriteTemplate;
    String gitUrlInternalTemplate;
    String token;
    Optional<String> protectedTagsPattern;
    List<String> protectedTagsAcceptedPatterns;

    public static GitBackendConfig fromConfig(String name, org.jboss.pnc.reqour.config.GitBackendConfig config) {
        return GitBackendConfig.builder()
                .name(name)
                .username(config.username())
                .url(config.url())
                .workspace(config.workspaceName())
                .workspaceId(config.workspaceId())
                .readOnlyTemplate(config.readOnlyTemplate())
                .readWriteTemplate(config.readWriteTemplate())
                .gitUrlInternalTemplate(config.gitUrlInternalTemplate())
                .token(config.token())
                .protectedTagsPattern(config.protectedTags().protectedTagsPattern())
                .protectedTagsAcceptedPatterns(
                        config.protectedTags().protectedTagsAcceptedPatterns().orElse(new ArrayList<>(List.of())))
                .build();
    }
}
