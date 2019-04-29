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
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GitHubArtifactExtractor implements WebhookArtifactExtractor {
  private final ObjectMapper objectMapper;

  @Autowired
  public GitHubArtifactExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public List<Artifact> getArtifacts(String source, Map payload) {
    PushEvent pushEvent = objectMapper.convertValue(payload, PushEvent.class);
    String sha = pushEvent.after;
    Set<String> affectedFiles =
        pushEvent.commits.stream()
            .map(
                c -> {
                  List<String> fs = new ArrayList<>();
                  fs.addAll(c.added);
                  fs.addAll(c.modified);
                  return fs;
                })
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

    return affectedFiles.stream()
        .map(
            f ->
                Artifact.builder()
                    .name(f)
                    .version(sha)
                    .type("github/file")
                    .reference(pushEvent.repository.contentsUrl.replace("{+path}", f))
                    .build())
        .collect(Collectors.toList());
  }

  @Override
  public boolean handles(String type, String source) {
    return type.equals("git") && source.equals("github");
  }

  @Data
  private static class PushEvent {
    private String after;
    private List<Commit> commits = new ArrayList<>();
    private Repository repository;
  }

  @Data
  private static class Commit {
    private List<String> added = new ArrayList<>();
    private List<String> modified = new ArrayList<>();
  }

  @Data
  private static class Repository {
    @JsonProperty("contents_url")
    private String contentsUrl;
  }
}
