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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.netflix.spinnaker.clouddriver.artifacts.config.HttpUrlRestrictions;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.github.GitHubAppAuthenticator;
import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
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
  void downloadWithGitHubAppAuth(@WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    GitHubAppAuthenticator authenticator = mock(GitHubAppAuthenticator.class);
    when(authenticator.getInstallationToken()).thenReturn("ghs_installation-token");

    GitHubArtifactAccount account = gitHubAppAccountBuilder(server.baseUrl()).build();

    runTestCase(
        server,
        new GitHubArtifactCredentials(account, okHttpClient, objectMapper, authenticator),
        m -> m.withHeader("Authorization", equalTo("token ghs_installation-token")));
  }

  @Test
  void gitHubAppAuthTakesPrecedenceOverTokenAuth(@WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    GitHubAppAuthenticator authenticator = mock(GitHubAppAuthenticator.class);
    when(authenticator.getInstallationToken()).thenReturn("ghs_installation-token");

    GitHubArtifactAccount account = gitHubAppAccountBuilder(server.baseUrl()).token("abc").build();

    runTestCase(
        server,
        new GitHubArtifactCredentials(account, okHttpClient, objectMapper, authenticator),
        m -> m.withHeader("Authorization", equalTo("token ghs_installation-token")));
  }

  @Test
  void gitHubAppInstallationTokenIsResolvedPerRequest(
      @WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitHubAppAuthenticator authenticator = mock(GitHubAppAuthenticator.class);
    when(authenticator.getInstallationToken()).thenReturn("ghs_token_one");

    GitHubArtifactAccount account = gitHubAppAccountBuilder(server.baseUrl()).build();
    GitHubArtifactCredentials credentials =
        new GitHubArtifactCredentials(account, okHttpClient, objectMapper, authenticator);

    runTestCase(
        server, credentials, m -> m.withHeader("Authorization", equalTo("token ghs_token_one")));

    // Simulate token rotation: the next download must pick up the fresh installation token
    when(authenticator.getInstallationToken()).thenReturn("ghs_token_two");

    runTestCase(
        server, credentials, m -> m.withHeader("Authorization", equalTo("token ghs_token_two")));
  }

  @Test
  void gitHubAppEndToEndTokenExchangeAndDownload(
      @TempDirectory.TempDir Path tempDir, @WiremockResolver.Wiremock WireMockServer server)
      throws Exception {
    // Real private key file, real GitHubAppAuthenticator - the wiremock server plays the GitHub
    // API for both the token exchange and the artifact download
    Path privateKeyFile = tempDir.resolve("gh-app-key.pem");
    Files.writeString(privateKeyFile, generatePkcs8Pem());

    server.stubFor(
        get(urlPathEqualTo("/app"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\": 12345, \"name\": \"test-app\"}")));
    server.stubFor(
        get(urlPathEqualTo("/app/installations/67890"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\": 67890, \"app_id\": 12345, \"account\": {\"login\": \"test-org\"}}")));
    server.stubFor(
        post(urlPathEqualTo("/app/installations/67890/access_tokens"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"token\": \"ghs_e2e_token\", \"expires_at\": \"2099-01-01T00:00:00Z\"}")));

    GitHubArtifactAccount account =
        gitHubAppAccountBuilder(server.baseUrl())
            .githubApp(
                new GitHubAppCredentials(
                    "12345", privateKeyFile.toString(), "67890", server.baseUrl()))
            .build();

    runTestCase(
        server,
        new GitHubArtifactCredentials(account, okHttpClient, objectMapper),
        m -> m.withHeader("Authorization", equalTo("token ghs_e2e_token")));

    // the installation token was minted with a JWT signed by the app's private key
    server.verify(
        1,
        postRequestedFor(urlPathEqualTo("/app/installations/67890/access_tokens"))
            .withHeader("Authorization", matching("Bearer eyJ.*")));
  }

  @Test
  void gitHubAppRejectsReferenceHostMismatchingApiBaseUrl(
      @WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitHubAppAuthenticator authenticator = mock(GitHubAppAuthenticator.class);
    when(authenticator.getInstallationToken()).thenReturn("ghs_installation-token");

    // The artifact reference lives on the wiremock host, but the account mints tokens against
    // api.github.com - the misconfiguration must be rejected with a directive error
    GitHubArtifactAccount account =
        gitHubAppAccountBuilder(GitHubAppCredentials.DEFAULT_API_BASE_URL).build();
    GitHubArtifactCredentials credentials =
        new GitHubArtifactCredentials(account, okHttpClient, objectMapper, authenticator);

    Artifact artifact =
        Artifact.builder()
            .reference(server.baseUrl() + METADATA_PATH)
            .version("master")
            .type("github/file")
            .build();

    IllegalArgumentException exception =
        Assertions.assertThrows(
            IllegalArgumentException.class, () -> credentials.download(artifact));
    assertThat(exception.getMessage()).contains("does not match githubApp.apiBaseUrl host");
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
  void downloadWithARestrictedUrl(@WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    // Github is interesting as ANY URL has to support BOTH the regular url & the download_url
    // expected by the response. Both must be on allowed hosts.
    final String downloadPath = "/download/spinnaker/testing/master/manifest.yml";

    GitHubArtifactCredentials.ContentMetadata contentMetadata =
        new GitHubArtifactCredentials.ContentMetadata()
            .setDownloadUrl(server.baseUrl() + downloadPath);

    server.stubFor(
        any(urlPathEqualTo(METADATA_PATH))
            .withQueryParam("ref", equalTo("master"))
            .willReturn(aResponse().withBody(objectMapper.writeValueAsString(contentMetadata))));

    server.stubFor(
        any(urlPathEqualTo(downloadPath)).willReturn(aResponse().withBody(FILE_CONTENTS)));

    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .urlRestrictions(
                HttpUrlRestrictions.builder()
                    .allowedHostnamesRegex("localhost|127\\.0\\.0\\.1")
                    .rejectLocalhost(false)
                    .build())
            .name("my-github-account")
            .build();
    GitHubArtifactCredentials credentials =
        new GitHubArtifactCredentials(account, okHttpClient, objectMapper);

    assertThat(
            credentials.download(
                Artifact.builder()
                    .reference(server.baseUrl() + METADATA_PATH)
                    .version("master")
                    .type("github/file")
                    .build()))
        .isNotNull();

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            credentials.download(
                Artifact.builder()
                    .reference("http://example.com/artifact")
                    .type("github/file")
                    .build()));
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

  private static String generatePkcs8Pem() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    return "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(Charsets.UTF_8))
            .encodeToString(keyPairGenerator.generateKeyPair().getPrivate().getEncoded())
        + "\n-----END PRIVATE KEY-----\n";
  }

  private GitHubArtifactAccount.GitHubArtifactAccountBuilder gitHubAppAccountBuilder(
      String apiBaseUrl) {
    GitHubAppCredentials githubApp =
        new GitHubAppCredentials("12345", "/path/to/key.pem", "67890", apiBaseUrl);
    return GitHubArtifactAccount.builder()
        .name("my-github-account")
        .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
        .githubApp(githubApp);
  }

  private void runTestCase(
      WireMockServer server,
      GitHubArtifactAccount account,
      Function<MappingBuilder, MappingBuilder> expectedAuth)
      throws IOException {
    runTestCase(
        server, new GitHubArtifactCredentials(account, okHttpClient, objectMapper), expectedAuth);
  }

  private void runTestCase(
      WireMockServer server,
      GitHubArtifactCredentials credentials,
      Function<MappingBuilder, MappingBuilder> expectedAuth)
      throws IOException {
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
