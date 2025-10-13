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

package com.netflix.spinnaker.clouddriver.artifacts.github;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
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
class GithubArtifactCredentialsTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OkHttpClient okHttpClient = new OkHttpClient();

  private final String METADATA_PATH = "/repos/spinnaker/testing/manifest.yml";
  private final String FILE_CONTENTS = "file contents";

  @Test
  void downloadWithToken(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .token("abc")
            .build();

    runTestCase(server, account, m -> m.withHeader("Authorization", equalTo("token abc")));
  }

  @Test
  void downloadWithTokenFromFile(
      @TempDirectory.TempDir Path tempDir, @WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "zzz".getBytes());

    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .tokenFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(server, account, m -> m.withHeader("Authorization", equalTo("token zzz")));
  }

  @Test
  void downloadWithTokenFromFileWithReloadHeaders(
      @TempDirectory.TempDir Path tempDir, @WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "zzz".getBytes());

    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .tokenFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(server, account, m -> m.withHeader("Authorization", equalTo("token zzz")));

    Files.write(authFile, "aaa".getBytes());

    runTestCase(server, account, m -> m.withHeader("Authorization", equalTo("token aaa")));
  }

  @Test
  void downloadWithBasicAuth(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .username("user")
            .password("passw0rd")
            .build();

    runTestCase(server, account, m -> m.withBasicAuth("user", "passw0rd"));
  }

  @Test
  void downloadWithBasicAuthFromFile(
      @TempDirectory.TempDir Path tempDir, @WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "someuser:somepassw0rd!".getBytes());

    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .usernamePasswordFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(server, account, m -> m.withBasicAuth("someuser", "somepassw0rd!"));
  }

  @Test
  void downloadWithNoAuth(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .build();

    runTestCase(server, account, m -> m.withHeader("Authorization", absent()));
  }

  @Test
  void useGitHubAPIs(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .token("zzz")
            .useContentAPI(true)
            .build();

    runTestCase(
        server,
        account,
        m ->
            m.withHeader("Authorization", equalTo("token zzz"))
                .withHeader("Accept", equalTo("application/vnd.github.v3.raw")));
  }

  @Test
  void useGitHubAPIsSpecificVersion(@WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .token("zzz")
            .useContentAPI(true)
            .githubAPIVersion("v10")
            .build();

    runTestCase(
        server,
        account,
        m ->
            m.withHeader("Authorization", equalTo("token zzz"))
                .withHeader("Accept", equalTo("application/vnd.github.v10.raw")));
  }

  @Test
  void downloadWithARestrictedUrl() throws IOException {
    // explicitly deny the test server we're hitting.
    // Github is interesting as ANY URL has to support BOTH the regular url & the download_url
    // expected by the response.
    // IT's VERY likely this is the same.  BUT should document this.
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .urlRestrictions(
                HttpUrlRestrictions.builder()
                    .allowedDomains(
                        List.of("www.http-response.com", "jsonplaceholder.typicode.com"))
                    .build())
            .name("my-bitbucket-account")
            .build();
    GitHubArtifactCredentials credentials =
        new GitHubArtifactCredentials(account, okHttpClient, objectMapper);
    Artifact artifact =
        Artifact.builder()
            .reference("http://example.com")
            .version("master")
            .type("github/file")
            .build();
    assertThat(
            credentials.download(
                Artifact.builder()
                    .reference(
                        "https://www.http-response.com/json?body={%22download_url%22:%22https://jsonplaceholder.typicode.com/posts/1%22}")
                    .build()))
        .isNotNull();
    Assertions.assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));
  }

  @Test
  void defaultRestrictLinkLocalAndLocalhost() {
    // explicitly deny the test server we're hitting.
    GitHubArtifactCredentials credentials =
        new GitHubArtifactCredentials(
            GitHubArtifactAccount.builder().name("my-github-account").build(),
            okHttpClient,
            objectMapper);
    Artifact artifact =
        Artifact.builder()
            .reference("http://localhost")
            .version("master")
            .type("github/file")
            .build();
    Assertions.assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));
  }

  private void runTestCase(
      WireMockServer server,
      GitHubArtifactAccount account,
      Function<MappingBuilder, MappingBuilder> expectedAuth)
      throws IOException {
    GitHubArtifactCredentials credentials =
        new GitHubArtifactCredentials(account, okHttpClient, objectMapper);

    Artifact artifact =
        Artifact.builder()
            .reference(server.baseUrl() + METADATA_PATH)
            .version("master")
            .type("github/file")
            .build();

    prepareServer(server, expectedAuth);

    assertThat(credentials.download(artifact))
        .hasSameContentAs(new ByteArrayInputStream(FILE_CONTENTS.getBytes(Charsets.UTF_8)));
    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }

  private void prepareServer(
      WireMockServer server, Function<MappingBuilder, MappingBuilder> withAuth) throws IOException {
    final String downloadPath = "/download/spinnaker/testing/master/manifest.yml";

    GitHubArtifactCredentials.ContentMetadata contentMetadata =
        new GitHubArtifactCredentials.ContentMetadata()
            .setDownloadUrl(server.baseUrl() + downloadPath);

    server.stubFor(
        withAuth.apply(
            any(urlPathEqualTo(METADATA_PATH))
                .withQueryParam("ref", equalTo("master"))
                .willReturn(
                    aResponse().withBody(objectMapper.writeValueAsString(contentMetadata)))));

    server.stubFor(
        withAuth.apply(
            any(urlPathEqualTo(METADATA_PATH))
                .withQueryParam("ref", equalTo("master"))
                .withHeader(
                    "Accept", new RegexPattern("application\\/vnd\\.github\\.v(\\d+)\\.raw"))
                .willReturn(aResponse().withBody(FILE_CONTENTS))));

    server.stubFor(
        withAuth.apply(
            any(urlPathEqualTo(downloadPath)).willReturn(aResponse().withBody(FILE_CONTENTS))));
  }
}
