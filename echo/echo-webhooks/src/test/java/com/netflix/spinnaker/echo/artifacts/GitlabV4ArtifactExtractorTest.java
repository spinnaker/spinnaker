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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GitlabV4ArtifactExtractorTest {
  private final GitlabV4ArtifactExtractor extractor =
      new GitlabV4ArtifactExtractor(EchoObjectMapper.getInstance());

  private static Map<String, Object> pushPayload(
      String homepage, String pathWithNamespace, List<String> added) {
    return Map.of(
        "after",
        "abc123",
        "commits",
        List.of(Map.of("added", added, "modified", List.of())),
        "project",
        Map.of("homepage", homepage, "path_with_namespace", pathWithNamespace));
  }

  private static Map<String, Object> pushPayload(String homepage, String pathWithNamespace) {
    return pushPayload(homepage, pathWithNamespace, List.of("manifests/app.yaml"));
  }

  @Test
  void onlyHandlesGitWebhooksFromGitlab() {
    assertThat(extractor.handles("git", "gitlab")).isTrue();
    assertThat(extractor.handles("git", "github")).isFalse();
    assertThat(extractor.handles("docker", "gitlab")).isFalse();
  }

  @Test
  void createsAFileArtifactForTheAddedFile() {
    List<Artifact> artifacts =
        extractor.getArtifacts(
            "gitlab", pushPayload("https://gitlab.example.com/test/repo", "test/repo"));

    assertThat(artifacts).hasSize(1);
    Artifact artifact = artifacts.get(0);
    assertThat(artifact.getType()).isEqualTo("gitlab/file");
    assertThat(artifact.getName()).isEqualTo("manifests/app.yaml");
    assertThat(artifact.getVersion()).isEqualTo("abc123");
    assertThat(artifact.getReference())
        .isEqualTo(
            "https://gitlab.example.com/api/v4/projects/test%2Frepo/repository/files/manifests%2Fapp.yaml/raw");
  }

  @Test
  void supportsSubgroupsAndAServerServedFromASubPath() {
    List<Artifact> artifacts =
        extractor.getArtifacts(
            "gitlab",
            pushPayload("https://example.com:8443/gitlab/group/sub/repo", "group/sub/repo"));

    assertThat(artifacts)
        .extracting(Artifact::getReference)
        .containsExactly(
            "https://example.com:8443/gitlab/api/v4/projects/group%2Fsub%2Frepo/repository/files/manifests%2Fapp.yaml/raw");
  }

  @Test
  void onlyRemovesThePathSuffixFromTheHomepage() {
    // The previous implementation removed every occurrence of the path from the homepage.
    List<Artifact> artifacts =
        extractor.getArtifacts("gitlab", pushPayload("https://gitlab.example.com/a/b/a/b", "a/b"));

    assertThat(artifacts)
        .extracting(Artifact::getReference)
        .allSatisfy(
            r -> assertThat(r).startsWith("https://gitlab.example.com/a/b/api/v4/projects/a%2Fb/"));
  }

  @Test
  void ignoresFilePathsThatWouldEscapeTheRepository() {
    List<Artifact> artifacts =
        extractor.getArtifacts(
            "gitlab",
            pushPayload(
                "https://gitlab.example.com/test/repo",
                "test/repo",
                List.of("../../../admin", "a/./b", "ok.yaml")));

    assertThat(artifacts).extracting(Artifact::getName).containsExactly("ok.yaml");
  }

  private static Stream<Arguments> invalidProjects() {
    return Stream.of(
        // homepage does not end with the project path
        Arguments.of("https://gitlab.example.com/somewhere/else", "test/repo"),
        Arguments.of("https://gitlab.example.com/test/repo/extra", "test/repo"),
        // dot segments, empty segments and a single segment in the project path
        Arguments.of("https://gitlab.example.com/test/../repo", "test/../repo"),
        Arguments.of("https://gitlab.example.com/test//repo", "test//repo"),
        Arguments.of("https://gitlab.example.com/repo", "repo"),
        // user info, query and fragment in the base of the url
        Arguments.of("https://user:pw@evil.example.com/test/repo", "test/repo"),
        Arguments.of("https://evil.example.com/?x=/test/repo", "test/repo"),
        Arguments.of("https://evil.example.com/#/test/repo", "test/repo"),
        // not http(s)
        Arguments.of("file:///test/repo", "test/repo"),
        Arguments.of("ftp://evil.example.com/test/repo", "test/repo"),
        Arguments.of("/test/repo", "test/repo"),
        // characters GitLab does not allow in a project path
        Arguments.of("https://gitlab.example.com/test/re po", "test/re po"),
        Arguments.of("https://gitlab.example.com/test/repo?x=1", "test/repo?x=1"));
  }

  @ParameterizedTest
  @MethodSource("invalidProjects")
  void ignoresAHomepageOrPathThatIsNotWhatGitlabWouldSend(
      String homepage, String pathWithNamespace) {
    assertThat(extractor.getArtifacts("gitlab", pushPayload(homepage, pathWithNamespace)))
        .isEmpty();
  }

  @Test
  void returnsNoArtifactsWhenTheProjectIsIncomplete() {
    assertThat(extractor.getArtifacts("gitlab", Map.of("after", "abc"))).isEmpty();
    assertThat(
            extractor.getArtifacts(
                "gitlab", Map.of("project", Map.of("path_with_namespace", "test/repo"))))
        .isEmpty();
    assertThat(
            extractor.getArtifacts(
                "gitlab", Map.of("project", Map.of("homepage", "https://gitlab.example.com/t/r"))))
        .isEmpty();
  }
}
