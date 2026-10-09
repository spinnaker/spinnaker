/*
 * Copyright 2026 spinnaker.io
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

import static com.netflix.spinnaker.echo.artifacts.GitWebhookArtifactReferences.OWNER_AND_REPOSITORY;
import static com.netflix.spinnaker.echo.artifacts.GitWebhookArtifactReferences.encodePath;
import static com.netflix.spinnaker.echo.artifacts.GitWebhookArtifactReferences.hasDotSegment;
import static com.netflix.spinnaker.echo.artifacts.GitWebhookArtifactReferences.isPlainHttpUrl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Produces {@code gitea/file} artifacts for the files added or modified by a Gitea push. The raw
 * file API URL is derived from the repository's {@code html_url}, so a Gitea server served from a
 * sub-path is handled as well.
 */
@Component
@Slf4j
public class GiteaArtifactExtractor implements WebhookArtifactExtractor {
  private final ObjectMapper objectMapper;

  @Autowired
  public GiteaArtifactExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public List<Artifact> getArtifacts(String source, Map payload) {
    PushEvent pushEvent = objectMapper.convertValue(payload, PushEvent.class);
    Repository repository = pushEvent.repository;
    if (repository == null || repository.htmlUrl == null || repository.fullName == null) {
      return new ArrayList<>();
    }

    // The payload is not trusted until the trigger verifies its signature, and these values end up
    // in the URL that clouddriver fetches, so only accept the shapes Gitea produces.
    if (!OWNER_AND_REPOSITORY.matcher(repository.fullName).matches()
        || hasDotSegment(repository.fullName)
        || !isPlainHttpUrl(repository.htmlUrl)) {
      log.warn("Ignoring Gitea push with an invalid repository full_name or html_url");
      return new ArrayList<>();
    }

    String suffix = "/" + repository.fullName;
    if (!repository.htmlUrl.endsWith(suffix)) {
      log.warn(
          "Unable to derive the Gitea API url from html_url {} for repository {}",
          repository.htmlUrl,
          repository.fullName);
      return new ArrayList<>();
    }
    String apiBaseUrl =
        String.format(
            "%s/api/v1/repos/%s/raw/",
            repository.htmlUrl.substring(0, repository.htmlUrl.length() - suffix.length()),
            repository.fullName);

    Set<String> affectedFiles =
        pushEvent.commits.stream()
            .flatMap(c -> Arrays.asList(c.added, c.modified).stream())
            .flatMap(Collection::stream)
            .filter(GitWebhookArtifactReferences::isSafeFilePath)
            .collect(Collectors.toSet());

    return affectedFiles.stream()
        .map(
            f ->
                Artifact.builder()
                    .name(f)
                    .version(pushEvent.after)
                    .type("gitea/file")
                    .reference(apiBaseUrl + encodePath(f))
                    .build())
        .collect(Collectors.toList());
  }

  @Override
  public boolean handles(String type, String source) {
    return type.equals("git") && source.equals("gitea");
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class PushEvent {
    private String after;
    private List<Commit> commits = new ArrayList<>();
    private Repository repository;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class Commit {
    private List<String> added = new ArrayList<>();
    private List<String> modified = new ArrayList<>();
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class Repository {
    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("full_name")
    private String fullName;
  }
}
