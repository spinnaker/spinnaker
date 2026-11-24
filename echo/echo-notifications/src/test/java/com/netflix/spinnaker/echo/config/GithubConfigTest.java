/*
 * Copyright 2026 Red Bull Media House GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.netflix.spinnaker.echo.github.GithubAppTokenProvider;
import com.netflix.spinnaker.echo.github.GithubTokenProvider;
import com.netflix.spinnaker.echo.test.config.Retrofit2NoneLogTestConfig;
import com.netflix.spinnaker.echo.test.config.Retrofit2TestConfig;
import com.netflix.spinnaker.kork.github.test.GitHubAppTestKeys;
import java.nio.file.Path;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GithubConfigTest {

  private static final String APP_ID = "12345";
  private static final String INSTALLATION_ID = "67890";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              Retrofit2TestConfig.class, Retrofit2NoneLogTestConfig.class, GithubConfig.class)
          .withBean(OkHttpClient.Builder.class, OkHttpClient.Builder::new)
          .withPropertyValues("github-status.enabled=true");

  private WireMockServer gitHub;
  private Path privateKeyFile;

  @BeforeEach
  void setUp(@TempDir Path tempDir) {
    gitHub = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    gitHub.start();
    privateKeyFile = GitHubAppTestKeys.writePkcs8Pem(tempDir.resolve("gh-app-key.pem"));

    gitHub.stubFor(get(urlPathEqualTo("/app")).willReturn(json("{\"id\": " + APP_ID + "}")));
    gitHub.stubFor(
        get(urlPathEqualTo("/app/installations/" + INSTALLATION_ID))
            .willReturn(
                json(
                    "{\"id\": "
                        + INSTALLATION_ID
                        + ", \"app_id\": "
                        + APP_ID
                        + ", \"account\": {\"login\": \"some-org\"}}")));
    gitHub.stubFor(
        post(urlPathEqualTo("/app/installations/" + INSTALLATION_ID + "/access_tokens"))
            .willReturn(
                json("{\"token\": \"ghs_app_token\", \"expires_at\": \"2099-01-01T00:00:00Z\"}")
                    .withStatus(201)));
  }

  @AfterEach
  void tearDown() {
    gitHub.stop();
  }

  @Test
  void tokenOnlyUsesThePersonalAccessToken() {
    runner
        .withPropertyValues("github-status.token=ghp_test")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(GithubTokenProvider.class).getToken("some-org/some-repo"))
                  .isEqualTo("ghp_test");
            });
  }

  @Test
  void githubAppOnlyMintsAnInstallationTokenFromTheStatusEndpoint() {
    runner
        .withPropertyValues(
            "github-status.endpoint=" + gitHub.baseUrl(),
            "github-status.github-app.app-id=" + APP_ID,
            "github-status.github-app.app-private-key-path=" + privateKeyFile,
            "github-status.github-app.app-installation-id=" + INSTALLATION_ID)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              GithubTokenProvider provider = context.getBean(GithubTokenProvider.class);
              assertThat(provider).isInstanceOf(GithubAppTokenProvider.class);
              assertThat(provider.getToken("some-org/some-repo")).isEqualTo("ghs_app_token");
              // api-base-url was not set, so the token was minted at github-status.endpoint
              gitHub.verify(
                  1,
                  postRequestedFor(
                      urlPathEqualTo("/app/installations/" + INSTALLATION_ID + "/access_tokens")));
            });
  }

  @Test
  void githubAppApiBaseUrlOverridesTheStatusEndpoint() {
    runner
        .withPropertyValues(
            "github-status.endpoint=http://localhost:1",
            "github-status.github-app.app-id=" + APP_ID,
            "github-status.github-app.app-private-key-path=" + privateKeyFile,
            "github-status.github-app.app-installation-id=" + INSTALLATION_ID,
            "github-status.github-app.api-base-url=" + gitHub.baseUrl())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(GithubTokenProvider.class).getToken("some-org/some-repo"))
                  .isEqualTo("ghs_app_token");
            });
  }

  @Test
  void tokenAndGithubAppTogetherFailAtStartup() {
    runner
        .withPropertyValues(
            "github-status.token=ghp_test",
            "github-status.github-app.app-id=" + APP_ID,
            "github-status.github-app.app-private-key-path=" + privateKeyFile)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining(
                        "Both github-status.token and github-status.github-app are configured"));
  }

  @Test
  void neitherTokenNorGithubAppFailsAtStartup() {
    runner.run(
        context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining(
                    "neither github-status.token nor github-status.github-app is configured"));
  }

  @Test
  void invalidGithubAppCredentialsFailAtStartup() {
    runner
        .withPropertyValues(
            "github-status.github-app.app-id=" + APP_ID,
            "github-status.github-app.app-private-key-path=" + privateKeyFile,
            "github-status.github-app.app-installation-id=not-a-number")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("appInstallationId must be numeric"));
  }

  private static ResponseDefinitionBuilder json(String body) {
    return aResponse().withHeader("Content-Type", "application/json").withBody(body);
  }
}
