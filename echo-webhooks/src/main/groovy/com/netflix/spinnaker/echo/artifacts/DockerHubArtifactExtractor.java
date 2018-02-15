/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.artifacts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DockerHubArtifactExtractor implements WebhookArtifactExtractor {
  final private ObjectMapper objectMapper;

  @Autowired
  public DockerHubArtifactExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public List<Artifact> getArtifacts(String source, Map payload) {
    WebhookEvent webhookEvent = objectMapper.convertValue(payload, WebhookEvent.class);
    Repository repository = webhookEvent.getRepository();
    PushData pushData = webhookEvent.getPushData();
    if (repository == null || pushData == null) {
      log.warn("Malformed push data from dockerhub: {}", payload);
      return Collections.emptyList();
    }

    String name = String.format("index.docker.io/%s", repository.getRepoName());
    String version = pushData.getTag();
    Map<String, Object> metadata = new ImmutableMap.Builder<String, Object>()
        .put("pusher", pushData.getPusher() != null ? pushData.getPusher() : "")
        .build();

    return Collections.singletonList(
        Artifact.builder()
            .type("docker/image")
            .reference(String.format("%s:%s", name, version))
            .name(name)
            .version(version)
            .provenance(webhookEvent.getCallbackUrl())
            .metadata(metadata)
            .build()
    );
  }

  @Override
  public boolean handles(String type, String source) {
    return (source != null && source.equals("dockerhub"));
  }

  @Data
  private static class WebhookEvent {
    @JsonProperty("callback_url")
    private String callbackUrl;
    @JsonProperty("push_data")
    private PushData pushData;
    private Repository repository;
  }

  @Data
  private static class PushData {
    private String tag;
    private String pusher;
  }

  @Data
  private static class Repository {
    @JsonProperty("repo_name")
    private String repoName;
  }
}
