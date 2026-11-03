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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GiteaArtifactExtractorTest {
  private final GiteaArtifactExtractor extractor =
      new GiteaArtifactExtractor(EchoObjectMapper.getInstance());

  private static Map<String, Object> pushPayload(String htmlUrl, String fullName) {
    return Map.of(
        "after",
        "abc123",
        "commits",
        List.of(
            Map.of("added", List.of("manifests/app.yaml"), "modified", List.of("README.md")),
            Map.of(
                "added", List.of(), "modified", List.of("manifests/app.yaml", "docs/my file.md"))),
        "repository",
        Map.of("html_url", htmlUrl, "full_name", fullName));
  }

  private static Map<String, Object> pushPayload(String htmlUrl) {
    return pushPayload(htmlUrl, "spinnaker/echo");
  }

  @Test
  void onlyHandlesGitWebhooksFromGitea() {
    assertThat(extractor.handles("git", "gitea")).isTrue();
    assertThat(extractor.handles("git", "github")).isFalse();
    assertThat(extractor.handles("docker", "gitea")).isFalse();
  }

  @Test
  void createsOneDeduplicatedFileArtifactPerAddedOrModifiedFile() {
    List<Artifact> artifacts =
        extractor.getArtifacts("gitea", pushPayload("https://gitea.example.com/spinnaker/echo"));

    assertThat(artifacts.stream().map(Artifact::getName).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("manifests/app.yaml", "README.md", "docs/my file.md");
    assertThat(artifacts)
        .allSatisfy(
            a -> {
              assertThat(a.getType()).isEqualTo("gitea/file");
              assertThat(a.getVersion()).isEqualTo("abc123");
            });
    assertThat(referenceOf(artifacts, "manifests/app.yaml"))
        .isEqualTo("https://gitea.example.com/api/v1/repos/spinnaker/echo/raw/manifests/app.yaml");
    assertThat(referenceOf(artifacts, "docs/my file.md"))
        .isEqualTo("https://gitea.example.com/api/v1/repos/spinnaker/echo/raw/docs/my%20file.md");
  }

  @Test
  void keepsTheSubPathOfAGiteaServerThatIsNotServedFromTheRoot() {
    List<Artifact> artifacts =
        extractor.getArtifacts("gitea", pushPayload("https://example.com/gitea/spinnaker/echo"));

    assertThat(referenceOf(artifacts, "README.md"))
        .isEqualTo("https://example.com/gitea/api/v1/repos/spinnaker/echo/raw/README.md");
  }

  @Test
  void returnsNoArtifactsWhenTheApiUrlCanNotBeDerived() {
    assertThat(
            extractor.getArtifacts(
                "gitea", pushPayload("https://gitea.example.com/somewhere/else")))
        .isEmpty();
    assertThat(extractor.getArtifacts("gitea", Map.of("after", "abc"))).isEmpty();
  }

  @Test
  void ignoresFilePathsThatWouldEscapeTheRepository() {
    Map<String, Object> payload =
        Map.of(
            "after",
            "abc123",
            "commits",
            List.of(
                Map.of(
                    "added",
                    List.of("../../../../user", "a/../../b", "./c", "a//b", "ok/file.yaml"),
                    "modified",
                    List.of(""))),
            "repository",
            Map.of(
                "html_url",
                "https://gitea.example.com/spinnaker/echo",
                "full_name",
                "spinnaker/echo"));

    assertThat(extractor.getArtifacts("gitea", payload))
        .extracting(Artifact::getName)
        .containsExactly("ok/file.yaml");
  }

  private static Stream<Arguments> invalidRepositories() {
    return Stream.of(
        Arguments.of("https://evil.example.com/x/../../admin?q=", "x/../../admin?q="),
        Arguments.of("https://gitea.example.com/spinnaker/echo", "../echo"),
        Arguments.of("https://gitea.example.com/spinnaker/echo?x=1", "spinnaker/echo?x=1"),
        Arguments.of("https://gitea.example.com/spinnaker/echo#frag", "spinnaker/echo#frag"),
        Arguments.of("https://gitea.example.com/a b/echo", "a b/echo"),
        Arguments.of("file:///spinnaker/echo", "spinnaker/echo"),
        Arguments.of("javascript:alert(1)//spinnaker/echo", "spinnaker/echo"),
        Arguments.of("https://gitea.example.com/spinnaker/echo", "spinnaker/echo/extra"));
  }

  @ParameterizedTest
  @MethodSource("invalidRepositories")
  void ignoresARepositoryFullNameOrHtmlUrlThatIsNotWhatGiteaWouldSend(
      String htmlUrl, String fullName) {
    assertThat(extractor.getArtifacts("gitea", pushPayload(htmlUrl, fullName))).isEmpty();
  }

  private static String referenceOf(List<Artifact> artifacts, String name) {
    return artifacts.stream()
        .filter(a -> name.equals(a.getName()))
        .findFirst()
        .orElseThrow()
        .getReference();
  }
}
