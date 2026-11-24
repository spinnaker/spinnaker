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

import static com.netflix.spinnaker.echo.artifacts.GitWebhookArtifactReferences.GROUPS_AND_PROJECT;
import static com.netflix.spinnaker.echo.artifacts.GitWebhookArtifactReferences.hasDotSegment;
import static com.netflix.spinnaker.echo.artifacts.GitWebhookArtifactReferences.isPlainHttpUrl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// GitlabV4ArtifactExtractor supports V4 of the Gitlab REST API
@Component
@Slf4j
public class GitlabV4ArtifactExtractor implements WebhookArtifactExtractor {
  private final ObjectMapper objectMapper;

  @Autowired
  public GitlabV4ArtifactExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public List<Artifact> getArtifacts(String source, Map payload) {
    PushEvent pushEvent = objectMapper.convertValue(payload, PushEvent.class);
    String sha = pushEvent.after;
    Project project = pushEvent.project;
    if (project == null || project.homepage == null || project.pathWithNamespace == null) {
      return new ArrayList<>();
    }

    // The payload is not trusted until the trigger verifies its signature, and these values end up
    // in the URL that clouddriver fetches, so only accept the shape GitLab produces:
    // <base url>/<group>[/<subgroup>...]/<project>
    String homepageSuffix = "/" + project.pathWithNamespace;
    if (!GROUPS_AND_PROJECT.matcher(project.pathWithNamespace).matches()
        || hasDotSegment(project.pathWithNamespace)
        || !project.homepage.endsWith(homepageSuffix)
        || !isPlainHttpUrl(
            project.homepage.substring(0, project.homepage.length() - homepageSuffix.length()))) {
      log.warn("Ignoring GitLab push with an invalid project homepage or path_with_namespace");
      return new ArrayList<>();
    }

    // since gitlab doesn't provide us with explicit API urls we have to assume the baseUrl from
    // other urls that are provided, e.g. http://example.com/test/repo -> http://example.com
    String gitlabBaseUrl =
        project.homepage.substring(0, project.homepage.length() - homepageSuffix.length());
    String apiBaseUrl =
        String.format(
            "%s/api/v4/projects/%s/repository/files",
            gitlabBaseUrl, URLEncoder.encode(project.pathWithNamespace));

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
            .filter(GitWebhookArtifactReferences::isSafeFilePath)
            .collect(Collectors.toSet());

    return affectedFiles.stream()
        .map(
            f ->
                Artifact.builder()
                    .name(f)
                    .version(sha)
                    .type("gitlab/file")
                    .reference(String.format("%s/%s/raw", apiBaseUrl, URLEncoder.encode(f)))
                    .build())
        .collect(Collectors.toList());
  }

  public boolean handles(String type, String source) {
    return type.equals("git") && source.equals("gitlab");
  }

  @Data
  private static class PushEvent {
    private String after;
    private List<Commit> commits = new ArrayList<>();
    private Project project;
  }

  @Data
  private static class Commit {
    private List<String> added = new ArrayList<>();
    private List<String> modified = new ArrayList<>();
  }

  @Data
  private static class Project {
    private String homepage;

    @JsonProperty("path_with_namespace")
    private String pathWithNamespace;
  }
}
