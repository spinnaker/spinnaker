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

package com.netflix.spinnaker.echo.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.config.GithubConfig;
import com.netflix.spinnaker.echo.test.config.Retrofit2NoneLogTestConfig;
import com.netflix.spinnaker.echo.test.config.Retrofit2TestConfig;
import com.netflix.spinnaker.kork.github.test.GitHubAppTestKeys;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Sends GitHub commit statuses authenticated as a GitHub App against a stubbed GitHub API. */
class GithubNotificationAgentGitHubAppTest {

  private static final String APP_ID = "12345";
  private static final String PINNED_INSTALLATION_ID = "67890";
  private static final String DERIVED_INSTALLATION_ID = "99999";
  private static final String STATUS_PATH = "/repos/some-org/some-repo/statuses/asdf";

  private WireMockServer gitHub;
  private ApplicationContextRunner runner;

  @BeforeEach
  void setUp(@TempDir Path tempDir) {
    gitHub = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    gitHub.start();
    Path privateKeyFile = GitHubAppTestKeys.writePkcs8Pem(tempDir.resolve("gh-app-key.pem"));

    gitHub.stubFor(get(urlPathEqualTo("/app")).willReturn(json("{\"id\": " + APP_ID + "}")));
    gitHub.stubFor(
        get(urlPathEqualTo("/app/installations/" + PINNED_INSTALLATION_ID))
            .willReturn(json(installationJson(PINNED_INSTALLATION_ID))));
    gitHub.stubFor(
        get(urlPathEqualTo("/repos/some-org/some-repo/installation"))
            .willReturn(json(installationJson(DERIVED_INSTALLATION_ID))));
    gitHub.stubFor(
        post(urlPathEqualTo("/app/installations/" + DERIVED_INSTALLATION_ID + "/access_tokens"))
            .willReturn(accessToken("ghs_derived")));
    gitHub.stubFor(
        get(urlPathMatching("/repos/some-org/some-repo/commits/.*"))
            .willReturn(json("{\"commit\": {\"message\": \"some message\"}}")));

    runner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                Retrofit2TestConfig.class,
                Retrofit2NoneLogTestConfig.class,
                GithubConfig.class,
                GithubNotificationAgent.class)
            .withBean(OkHttpClient.Builder.class, OkHttpClient.Builder::new)
            .withPropertyValues(
                "spinnaker.base-url=http://spinnaker.io",
                "github-status.enabled=true",
                "github-status.endpoint=" + gitHub.baseUrl(),
                "github-status.github-app.app-id=" + APP_ID,
                "github-status.github-app.app-private-key-path=" + privateKeyFile);
  }

  @AfterEach
  void tearDown() {
    gitHub.stop();
  }

  @Test
  void sendsStatusWithThePinnedInstallationToken() {
    stubPinnedAccessToken();
    gitHub.stubFor(post(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse().withStatus(201)));

    runner
        .withPropertyValues(
            "github-status.github-app.app-installation-id=" + PINNED_INSTALLATION_ID)
        .run(context -> send(context.getBean(GithubNotificationAgent.class), null));

    assertThat(authorizations(postRequestedFor(urlPathEqualTo(STATUS_PATH))))
        .containsExactly("token ghs_pinned");
    assertThat(
            authorizations(
                getRequestedFor(urlPathMatching("/repos/some-org/some-repo/commits/.*"))))
        .containsExactly("token ghs_pinned");
  }

  @Test
  void sendsStatusWithTheInstallationTokenDerivedFromTheRepository() {
    gitHub.stubFor(post(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse().withStatus(201)));

    runner
        .withPropertyValues("github-status.github-app.allowed-organizations=some-org")
        .run(context -> send(context.getBean(GithubNotificationAgent.class), null));

    assertThat(authorizations(postRequestedFor(urlPathEqualTo(STATUS_PATH))))
        .containsExactly("token ghs_derived");
  }

  @Test
  void skipsTheStatusWhenTheRepositoryCannotBeParsedInDerivedMode() {
    runner.run(
        context ->
            send(context.getBean(GithubNotificationAgent.class), Map.of("repo", "not-a-repo")));

    assertThat(gitHub.findAll(postRequestedFor(anyUrl()))).isEmpty();
  }

  @Test
  void skipsTheStatusWhenTheOwnerIsNotAnAllowedOrganization() {
    runner
        .withPropertyValues("github-status.github-app.allowed-organizations=other-org")
        .run(context -> send(context.getBean(GithubNotificationAgent.class), null));

    assertThat(gitHub.findAll(postRequestedFor(anyUrl()))).isEmpty();
  }

  @Test
  void mintsANewPinnedTokenWhenGitHubRejectsTheCachedOne() {
    String accessTokens = "/app/installations/" + PINNED_INSTALLATION_ID + "/access_tokens";
    gitHub.stubFor(
        post(urlPathEqualTo(accessTokens))
            .inScenario("token")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(accessToken("ghs_revoked"))
            .willSetStateTo("revoked"));
    gitHub.stubFor(
        post(urlPathEqualTo(accessTokens))
            .inScenario("token")
            .whenScenarioStateIs("revoked")
            .willReturn(accessToken("ghs_renewed")));
    gitHub.stubFor(
        post(urlPathEqualTo(STATUS_PATH))
            .inScenario("status")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(401))
            .willSetStateTo("accepted"));
    gitHub.stubFor(
        post(urlPathEqualTo(STATUS_PATH))
            .inScenario("status")
            .whenScenarioStateIs("accepted")
            .willReturn(aResponse().withStatus(201)));

    runner
        .withPropertyValues(
            "github-status.github-app.app-installation-id=" + PINNED_INSTALLATION_ID)
        .run(context -> send(context.getBean(GithubNotificationAgent.class), null));

    assertThat(authorizations(postRequestedFor(urlPathEqualTo(STATUS_PATH))))
        .containsExactly("token ghs_revoked", "token ghs_renewed");
  }

  private void stubPinnedAccessToken() {
    gitHub.stubFor(
        post(urlPathEqualTo("/app/installations/" + PINNED_INSTALLATION_ID + "/access_tokens"))
            .willReturn(accessToken("ghs_pinned")));
  }

  private static void send(GithubNotificationAgent agent, Map<String, Object> preference) {
    Event event = new Event();
    event.setContent(
        Map.of(
            "execution",
            Map.of(
                "id",
                "1",
                "name",
                "foo-pipeline",
                "trigger",
                Map.of(
                    "buildInfo",
                    Map.of(
                        "name",
                        "some-org/some-repo",
                        "scm",
                        List.of(Map.of("branch", "master", "name", "master", "sha1", "asdf")))))));
    agent.sendNotifications(preference, "whatever", event, Map.of("type", "pipeline"), "starting");
  }

  private List<String> authorizations(RequestPatternBuilder requests) {
    return gitHub.findAll(requests).stream()
        .map(request -> request.getHeader("Authorization"))
        .toList();
  }

  private static String installationJson(String installationId) {
    return "{\"id\": "
        + installationId
        + ", \"app_id\": "
        + APP_ID
        + ", \"account\": {\"login\": \"some-org\"}}";
  }

  private static ResponseDefinitionBuilder accessToken(String token) {
    return json("{\"token\": \"" + token + "\", \"expires_at\": \"2099-01-01T00:00:00Z\"}")
        .withStatus(201);
  }

  private static ResponseDefinitionBuilder json(String body) {
    return aResponse().withHeader("Content-Type", "application/json").withBody(body);
  }
}
