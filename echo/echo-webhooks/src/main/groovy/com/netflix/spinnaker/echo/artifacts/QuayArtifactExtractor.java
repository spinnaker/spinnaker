/*
 * Copyright 2018 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class QuayArtifactExtractor implements WebhookArtifactExtractor {

  private final ObjectMapper objectMapper;

  @Autowired
  public QuayArtifactExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public List<Artifact> getArtifacts(String source, Map payload) {
    PushEvent pushEvent = objectMapper.convertValue(payload, PushEvent.class);
    String repository = pushEvent.getRepository();
    List<String> updatedTags = pushEvent.getUpdatedTags();
    if (repository == null || updatedTags.size() == 0) {
      log.warn("Malformed push event from quay: {}", payload);
      return Collections.emptyList();
    }

    return updatedTags.stream()
        .map(
            a ->
                Artifact.builder()
                    .type("docker/image")
                    .reference(String.format("%s:%s", pushEvent.getDockerUrl(), a))
                    .name(pushEvent.getDockerUrl())
                    .version(a)
                    .provenance(pushEvent.getHomepage())
                    .build())
        .collect(Collectors.toList());
  }

  @Override
  public boolean handles(String type, String source) {
    return (source != null && source.equals("quay"));
  }

  @Data
  private static class PushEvent {
    private String repository;
    private String namespace;
    private String name;
    private String homepage;

    @JsonProperty("docker_url")
    private String dockerUrl;

    @JsonProperty("updated_tags")
    private List<String> updatedTags;
  }
}
