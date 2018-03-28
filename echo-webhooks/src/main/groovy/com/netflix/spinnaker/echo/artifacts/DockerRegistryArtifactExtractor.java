/*
 * Copyright 2018 Joel Wilsson
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DockerRegistryArtifactExtractor implements WebhookArtifactExtractor {
  final private ObjectMapper objectMapper;
  final private String MEDIA_TYPE_V1_MANIFEST = "application/vnd.docker.distribution.manifest.v1+json";
  final private String MEDIA_TYPE_V2_MANIFEST = "application/vnd.docker.distribution.manifest.v2+json";

  @Autowired
  public DockerRegistryArtifactExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public List<Artifact> getArtifacts(String source, Map payload) {
    Notification notification = objectMapper.convertValue(payload, Notification.class);
    List<Event> pushEvents = notification.getEvents().stream()
      .filter(e -> e.action.equals("push")
        && e.getTarget() != null
        && (e.getTarget().getMediaType().equals(MEDIA_TYPE_V1_MANIFEST) || e.getTarget().getMediaType().equals(MEDIA_TYPE_V2_MANIFEST))
        && e.getTarget().getRepository() != null
        && (e.getTarget().getTag() != null || e.getTarget().getDigest() != null))
      .collect(Collectors.toList());

    if (pushEvents.isEmpty()) {
      return Collections.EMPTY_LIST;
    }

    return pushEvents.stream()
      .map(e -> {
        String tag;
        String tagSeparator = ":";
        if (e.getTarget().getTag() != null) {
          tag = e.getTarget().getTag();
        } else if (e.getTarget().getDigest() != null) {
          tag = e.getTarget().getDigest();
          tagSeparator = "@";
        } else {
          return null;
        }
        String host = (e.getRequest() != null && e.getRequest().getHost() != null) ? (e.getRequest().getHost() + "/") : "";
        return Artifact.builder()
          .name(host + e.getTarget().getRepository())
          .version(tag)
          .reference(host + e.getTarget().getRepository() + tagSeparator + tag)
          .type("docker/image")
          .build();
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @Override
  public boolean handles(String type, String source) {
    return (source != null && source.equals("dockerregistry"));
  }

  @Data
  private static class Notification {
    private List<Event> events;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class Event {
    private String action;
    private Target target;
    private Source request;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class Target {
    private String mediaType;
    private String digest;
    private String repository;
    private String url;
    private String tag;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class Source {
    private String addr;
    private String host;
  }
}
