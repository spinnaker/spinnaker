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
import static org.assertj.core.api.Assertions.tuple;

import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GitHubArtifactExtractorTest {
  private final GitHubArtifactExtractor extractor =
      new GitHubArtifactExtractor(EchoObjectMapper.getInstance());

  private static Map<String, Object> pushPayload(
      String contentsUrl, String fullName, List<String> added) {
    return Map.of(
        "after",
        "abc123",
        "commits",
        List.of(Map.of("added", added, "modified", List.of("README.md"))),
        "repository",
        Map.of("contents_url", contentsUrl, "full_name", fullName));
  }

  private static Map<String, Object> pushPayload(String contentsUrl, String fullName) {
    return pushPayload(contentsUrl, fullName, List.of("manifests/app.yaml"));
  }

  @Test
  void onlyHandlesGitWebhooksFromGitHub() {
    assertThat(extractor.handles("git", "github")).isTrue();
    assertThat(extractor.handles("git", "gitlab")).isFalse();
    assertThat(extractor.handles("docker", "github")).isFalse();
  }

  @Test
  void createsAFileArtifactPerAddedOrModifiedFile() {
    List<Artifact> artifacts =
        extractor.getArtifacts(
            "github",
            pushPayload(
                "https://api.github.com/repos/Codertocat/Hello-World/contents/{+path}",
                "Codertocat/Hello-World"));

    assertThat(artifacts)
        .extracting(
            Artifact::getName, Artifact::getType, Artifact::getVersion, Artifact::getReference)
        .containsExactlyInAnyOrder(
            tuple(
                "manifests/app.yaml",
                "github/file",
                "abc123",
                "https://api.github.com/repos/Codertocat/Hello-World/contents/manifests/app.yaml"),
            tuple(
                "README.md",
                "github/file",
                "abc123",
                "https://api.github.com/repos/Codertocat/Hello-World/contents/README.md"));
  }

  @Test
  void acceptsAGitHubEnterpriseApiUrl() {
    List<Artifact> artifacts =
        extractor.getArtifacts(
            "github",
            pushPayload("https://ghe.example.com:8443/api/v3/repos/o/r/contents/{+path}", "o/r"));

    assertThat(artifacts)
        .extracting(Artifact::getReference)
        .contains("https://ghe.example.com:8443/api/v3/repos/o/r/contents/manifests/app.yaml");
  }

  @Test
  void percentEncodesFileNames() {
    List<Artifact> artifacts =
        extractor.getArtifacts(
            "github",
            pushPayload(
                "https://api.github.com/repos/o/r/contents/{+path}",
                "o/r",
                List.of("docs/my file?x=1#frag.md")));

    assertThat(artifacts)
        .extracting(Artifact::getReference)
        .contains("https://api.github.com/repos/o/r/contents/docs/my%20file%3Fx=1%23frag.md");
  }

  @Test
  void ignoresFilePathsThatWouldEscapeTheRepository() {
    List<Artifact> artifacts =
        extractor.getArtifacts(
            "github",
            pushPayload(
                "https://api.github.com/repos/o/r/contents/{+path}",
                "o/r",
                List.of("../../../admin", "a/./b", "a//b", "ok.yaml")));

    assertThat(artifacts)
        .extracting(Artifact::getName)
        .containsExactlyInAnyOrder("ok.yaml", "README.md");
  }

  private static Stream<Arguments> invalidRepositories() {
    return Stream.of(
        // contents_url does not belong to the repository the payload names
        Arguments.of("https://api.github.com/repos/other/repo/contents/{+path}", "o/r"),
        Arguments.of("https://api.github.com/repos/o/r/contents/", "o/r"),
        Arguments.of("https://api.github.com/repos/o/r/contents/{+path}/extra", "o/r"),
        // dot segments in the repository name
        Arguments.of("https://api.github.com/repos/o/../contents/{+path}", "o/.."),
        Arguments.of("https://api.github.com/repos/../r/contents/{+path}", "../r"),
        // user info, query and fragment in the base of the url
        Arguments.of("https://user:pw@evil.example.com/repos/o/r/contents/{+path}", "o/r"),
        Arguments.of("https://evil.example.com/?x=/repos/o/r/contents/{+path}", "o/r"),
        Arguments.of("https://evil.example.com/#/repos/o/r/contents/{+path}", "o/r"),
        // not http(s)
        Arguments.of("file:///repos/o/r/contents/{+path}", "o/r"),
        Arguments.of("ftp://evil.example.com/repos/o/r/contents/{+path}", "o/r"),
        Arguments.of("/repos/o/r/contents/{+path}", "o/r"),
        // characters GitHub does not allow in a repository name
        Arguments.of("https://api.github.com/repos/o/r s/contents/{+path}", "o/r s"),
        Arguments.of("https://api.github.com/repos/o/r?/contents/{+path}", "o/r?"));
  }

  @ParameterizedTest
  @MethodSource("invalidRepositories")
  void ignoresAContentsUrlOrFullNameThatIsNotWhatGitHubWouldSend(
      String contentsUrl, String fullName) {
    assertThat(extractor.getArtifacts("github", pushPayload(contentsUrl, fullName))).isEmpty();
  }

  @Test
  void returnsNoArtifactsWhenTheRepositoryIsIncomplete() {
    assertThat(extractor.getArtifacts("github", Map.of("after", "abc"))).isEmpty();
    assertThat(extractor.getArtifacts("github", Map.of("repository", Map.of("full_name", "o/r"))))
        .isEmpty();
    assertThat(
            extractor.getArtifacts(
                "github",
                Map.of(
                    "repository",
                    Map.of("contents_url", "https://api.github.com/repos/o/r/contents/{+path}"))))
        .isEmpty();
  }
}
