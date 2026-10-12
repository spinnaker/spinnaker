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

package com.netflix.spinnaker.clouddriver.artifacts.gitea;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.url.UrlRestrictionsProperties;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.TempDirectory;
import ru.lanwen.wiremock.ext.WiremockResolver;

@ExtendWith({WiremockResolver.class, TempDirectory.class})
class GiteaArtifactCredentialsTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OkHttpClient okHttpClient = new OkHttpClient();

  private final String DOWNLOAD_PATH = "/api/v1/repos/spinnaker/testing/raw/manifest.yml";
  private final String FILE_CONTENTS = "file contents";

  @Test
  void downloadWithToken(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GiteaArtifactAccount account =
        GiteaArtifactAccount.builder()
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .rejectLocalhost(false)
                    .allowedDomains(List.of("localhost"))
                    .build())
            .name("my-gitea-account")
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

    GiteaArtifactAccount account =
        GiteaArtifactAccount.builder()
            .name("my-gitea-account")
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .rejectLocalhost(false)
                    .allowedDomains(List.of("localhost"))
                    .build())
            .tokenFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(server, account, m -> m.withHeader("Authorization", equalTo("token zzz")));
  }

  @Test
  void downloadWithNoAuth(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GiteaArtifactAccount account =
        GiteaArtifactAccount.builder()
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .rejectLocalhost(false)
                    .allowedDomains(List.of("localhost"))
                    .build())
            .name("my-gitea-account")
            .build();

    runTestCase(server, account, m -> m.withHeader("Authorization", absent()));
  }

  @Test
  void sendsVersionAsRefQueryParameter(@WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    GiteaArtifactAccount account =
        GiteaArtifactAccount.builder()
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .rejectLocalhost(false)
                    .allowedDomains(List.of("localhost"))
                    .build())
            .name("my-gitea-account")
            .build();
    GiteaArtifactCredentials credentials = new GiteaArtifactCredentials(account, okHttpClient);
    server.stubFor(
        get(urlPathEqualTo(DOWNLOAD_PATH))
            .withQueryParam("ref", equalTo("feature/x"))
            .willReturn(aResponse().withBody(FILE_CONTENTS)));

    Artifact artifact =
        Artifact.builder()
            .reference(server.baseUrl() + DOWNLOAD_PATH + "?ref=ignored")
            .version("feature/x")
            .type("gitea/file")
            .build();

    assertThat(credentials.download(artifact))
        .hasSameContentAs(new ByteArrayInputStream(FILE_CONTENTS.getBytes(Charsets.UTF_8)));
    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }

  @Test
  void omitsRefWhenNoVersionSoGiteaUsesDefaultBranch(
      @WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GiteaArtifactAccount account =
        GiteaArtifactAccount.builder()
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .rejectLocalhost(false)
                    .allowedDomains(List.of("localhost"))
                    .build())
            .name("my-gitea-account")
            .build();
    GiteaArtifactCredentials credentials = new GiteaArtifactCredentials(account, okHttpClient);
    server.stubFor(
        get(urlPathEqualTo(DOWNLOAD_PATH))
            .withQueryParam("ref", absent())
            .willReturn(aResponse().withBody(FILE_CONTENTS)));

    Artifact artifact =
        Artifact.builder().reference(server.baseUrl() + DOWNLOAD_PATH).type("gitea/file").build();

    assertThat(credentials.download(artifact))
        .hasSameContentAs(new ByteArrayInputStream(FILE_CONTENTS.getBytes(Charsets.UTF_8)));
    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/user",
        "/api/v1/repos/spinnaker/testing",
        "/api/v1/repos/spinnaker/testing/raw",
        "/api/v1/repos/spinnaker/testing/raw/",
        "/api/v1/repos/spinnaker/testing/contents/manifest.yml",
        "/api/v1/admin/users",
        // dot segments, plain and percent-encoded, are normalized and must not escape the shape
        "/api/v1/repos/spinnaker/testing/raw/../../../../user",
        "/api/v1/repos/spinnaker/testing/raw/%2e%2e/%2e%2e/%2e%2e/%2e%2e/user",
        "/api/v1/repos//testing/raw/manifest.yml"
      })
  void rejectsReferencesThatAreNotRawFileUrls(String path) {
    GiteaArtifactAccount account =
        GiteaArtifactAccount.builder()
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .rejectLocalhost(false)
                    .allowedDomains(List.of("localhost"))
                    .build())
            .name("my-gitea-account")
            .token("abc")
            .build();
    GiteaArtifactCredentials credentials = new GiteaArtifactCredentials(account, okHttpClient);
    Artifact artifact =
        Artifact.builder()
            .reference("http://localhost:1" + path)
            .version("main")
            .type("gitea/file")
            .build();

    // fails before any request is made, so no token can be sent anywhere
    assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));
  }

  @Test
  void acceptsRawFileUrlsBehindAPathPrefix(@WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    GiteaArtifactAccount account =
        GiteaArtifactAccount.builder()
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .rejectLocalhost(false)
                    .allowedDomains(List.of("localhost"))
                    .build())
            .name("my-gitea-account")
            .build();
    GiteaArtifactCredentials credentials = new GiteaArtifactCredentials(account, okHttpClient);
    server.stubFor(
        get(urlPathEqualTo("/gitea/api/v1/repos/spinnaker/testing/raw/dir/manifest.yml"))
            .willReturn(aResponse().withBody(FILE_CONTENTS)));

    Artifact artifact =
        Artifact.builder()
            .reference(
                server.baseUrl() + "/gitea/api/v1/repos/spinnaker/testing/raw/dir/manifest.yml")
            .type("gitea/file")
            .build();

    assertThat(credentials.download(artifact))
        .hasSameContentAs(new ByteArrayInputStream(FILE_CONTENTS.getBytes(Charsets.UTF_8)));
  }

  /**
   * Pinning the account to the Gitea host with allowedDomains restricts which hosts the account's
   * token is sent to. Mirrors the host matching cases of the git/repo account's allowedHosts, using
   * the shared UrlRestrictions.allowedDomains.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://evil.example.org/api/v1/repos/o/r/raw/f.yml",
        // the allowed name as a prefix or suffix of another host
        "https://gitea.example.com.evil.example.org/api/v1/repos/o/r/raw/f.yml",
        "https://evilgitea.example.com/api/v1/repos/o/r/raw/f.yml",
        // the allowed name only appears in the userinfo, the real host is evil.example.org
        "https://gitea.example.com@evil.example.org/api/v1/repos/o/r/raw/f.yml",
        "https://gitea.example.com:pw@evil.example.org/api/v1/repos/o/r/raw/f.yml"
      })
  void rejectsHostsOutsideTheAllowedDomains(String reference) {
    GiteaArtifactAccount account =
        GiteaArtifactAccount.builder()
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .allowedDomains(List.of("gitea\\.example\\.com"))
                    .build())
            .name("my-gitea-account")
            .token("abc")
            .build();
    GiteaArtifactCredentials credentials = new GiteaArtifactCredentials(account, okHttpClient);
    Artifact artifact =
        Artifact.builder().reference(reference).version("main").type("gitea/file").build();

    assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));
  }

  @Test
  void blockByDefaultIfNoRestrictionsAreSetToLocalhost() {
    // explicitly deny the test server we're hitting.
    GiteaArtifactAccount account = GiteaArtifactAccount.builder().name("my-gitea-account").build();
    GiteaArtifactCredentials credentials = new GiteaArtifactCredentials(account, okHttpClient);
    Artifact artifact =
        Artifact.builder()
            .reference("http://localhost")
            .version("master")
            .type("gitea/file")
            .build();
    Assertions.assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));
  }

  @Test
  void obeyRestrictionsWhenSet(@WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    GiteaArtifactAccount account =
        GiteaArtifactAccount.builder()
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .allowedHostnamesRegex("localhost|127\\.0\\.0\\.1")
                    .rejectLocalhost(false)
                    .build())
            .name("my-gitea-account")
            .build();
    GiteaArtifactCredentials credentials = new GiteaArtifactCredentials(account, okHttpClient);

    server.stubFor(
        any(urlPathEqualTo(DOWNLOAD_PATH))
            .willReturn(aResponse().withStatus(200).withBody(FILE_CONTENTS)));

    assertThat(
            credentials.download(
                Artifact.builder()
                    .reference(server.baseUrl() + DOWNLOAD_PATH)
                    .type("gitea/file")
                    .build()))
        .isNotNull();

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            credentials.download(
                Artifact.builder()
                    .reference("http://example.com/artifact")
                    .type("gitea/file")
                    .build()));
  }

  private void runTestCase(
      WireMockServer server,
      GiteaArtifactAccount account,
      Function<MappingBuilder, MappingBuilder> expectedAuth)
      throws IOException {
    GiteaArtifactCredentials credentials = new GiteaArtifactCredentials(account, okHttpClient);

    Artifact artifact =
        Artifact.builder()
            .reference(server.baseUrl() + DOWNLOAD_PATH)
            .version("master")
            .type("gitea/file")
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
