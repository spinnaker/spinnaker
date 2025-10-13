/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.gitlab;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.netflix.spinnaker.clouddriver.artifacts.config.HttpUrlRestrictions;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import okhttp3.OkHttpClient;
import org.apache.commons.io.Charsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;
import ru.lanwen.wiremock.ext.WiremockResolver;

@ExtendWith({WiremockResolver.class, TempDirectory.class})
class GitlabArtifactCredentialsTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OkHttpClient okHttpClient = new OkHttpClient();

  private final String DOWNLOAD_PATH = "/repos/spinnaker/testing/manifest.yml";
  private final String FILE_CONTENTS = "file contents";

  @Test
  void downloadWithToken(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitlabArtifactAccount account =
        GitlabArtifactAccount.builder()
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .name("my-gitlab-account")
            .token("abc")
            .build();

    runTestCase(server, account, m -> m.withHeader("Private-Token", equalTo("abc")));
  }

  @Test
  void downloadWithTokenFromFile(
      @TempDirectory.TempDir Path tempDir, @WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "zzz".getBytes());

    GitlabArtifactAccount account =
        GitlabArtifactAccount.builder()
            .name("my-gitlab-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .tokenFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(server, account, m -> m.withHeader("Private-Token", equalTo("zzz")));
  }

  @Test
  void downloadWithNoAuth(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitlabArtifactAccount account =
        GitlabArtifactAccount.builder()
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .name("my-gitlab-account")
            .build();

    runTestCase(server, account, m -> m.withHeader("Authorization", absent()));
  }

  @Test
  void blockByDefaultIfNoRestrictionsAreSetToLocalhost() {
    // explicitly deny the test server we're hitting.
    GitlabArtifactAccount account =
        GitlabArtifactAccount.builder().name("my-gitlab-account").build();
    GitlabArtifactCredentials credentials = new GitlabArtifactCredentials(account, okHttpClient);
    Artifact artifact =
        Artifact.builder()
            .reference("http://localhost")
            .version("master")
            .type("gitlab/file")
            .build();
    Assertions.assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));
  }

  @Test
  void obeyRestrictionsWhenSet() throws IOException {
    // explicitly deny the test server we're hitting.
    GitlabArtifactAccount account =
        GitlabArtifactAccount.builder()
            .urlRestrictions(
                HttpUrlRestrictions.builder().allowedDomains(List.of("example.com")).build())
            .name("my-gitlab-account")
            .build();
    GitlabArtifactCredentials credentials = new GitlabArtifactCredentials(account, okHttpClient);
    assertThat(credentials.download(Artifact.builder().reference("http://example.com").build()))
        .isNotNull();
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> credentials.download(Artifact.builder().reference("http://google.com").build()));
  }

  private void runTestCase(
      WireMockServer server,
      GitlabArtifactAccount account,
      Function<MappingBuilder, MappingBuilder> expectedAuth)
      throws IOException {
    GitlabArtifactCredentials credentials = new GitlabArtifactCredentials(account, okHttpClient);

    Artifact artifact =
        Artifact.builder()
            .reference(server.baseUrl() + DOWNLOAD_PATH)
            .version("master")
            .type("gitlab/file")
            .build();

    prepareServer(server, expectedAuth);

    assertThat(credentials.download(artifact))
        .hasSameContentAs(new ByteArrayInputStream(FILE_CONTENTS.getBytes(Charsets.UTF_8)));
    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }

  private void prepareServer(
      WireMockServer server, Function<MappingBuilder, MappingBuilder> withAuth) {
    server.stubFor(
        withAuth.apply(
            any(urlPathEqualTo(DOWNLOAD_PATH)).willReturn(aResponse().withBody(FILE_CONTENTS))));
  }
}
