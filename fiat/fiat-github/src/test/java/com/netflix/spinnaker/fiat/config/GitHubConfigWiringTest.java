/*
 * Copyright 2026 Razorpay.
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

package com.netflix.spinnaker.fiat.config;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.providers.HealthTrackable;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import com.netflix.spinnaker.fiat.roles.github.GithubTeamsHealthTrackable;
import com.netflix.spinnaker.fiat.roles.github.GithubTeamsUserRolesProvider;
import com.netflix.spinnaker.kork.github.test.GitHubAppTestKeys;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Starts the GitHub configuration in a Spring context, to check that the provider receives the
 * GitHub App authenticator (which it needs to drop a token GitHub rejects) and still starts without
 * one when a personal access token is used.
 */
class GitHubConfigWiringTest {

  private static final String INSTALLATION_ID = "67890";

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @TempDir Path tempDir;

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(
              GitHubProperties.class, GitHubConfig.class, GithubTeamsUserRolesProvider.class)
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withPropertyValues(
              "auth.group-membership.service=github",
              "auth.group-membership.github.organization=test-org",
              "auth.group-membership.github.health-warm-interval-seconds=0");

  @Test
  void shouldGiveTheProviderTheGitHubAppAuthenticator() {
    stubGitHubAppTokenExchange();
    Path privateKey = GitHubAppTestKeys.writePkcs8Pem(tempDir.resolve("key.pem"));

    contextRunner
        .withPropertyValues(
            "auth.group-membership.github.base-url=" + wireMock.baseUrl(),
            "auth.group-membership.github.app-id=12345",
            "auth.group-membership.github.private-key-path=" + privateKey,
            "auth.group-membership.github.installation-id=" + INSTALLATION_ID)
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              // Registered only when the provider has an authenticator to read the expiry from
              assertNotNull(
                  context
                      .getBean(MeterRegistry.class)
                      .find("fiat.github.tokenSecondsToExpiry")
                      .gauge());
            });
  }

  @Test
  void shouldStartWithoutAnAuthenticatorWhenUsingAPersonalAccessToken() {
    contextRunner
        .withPropertyValues(
            "auth.group-membership.github.base-url=" + wireMock.baseUrl(),
            "auth.group-membership.github.access-token=ghp_test_token")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              assertNotNull(context.getBean(GithubTeamsUserRolesProvider.class));
              assertNull(
                  context
                      .getBean(MeterRegistry.class)
                      .find("fiat.github.tokenSecondsToExpiry")
                      .gauge());
            });
  }

  @Test
  void shouldLeaveGitHubOutOfFiatHealthByDefault() {
    // A GitHub outage at pod startup would otherwise keep the pod DOWN, and with it Fiat
    contextRunner
        .withPropertyValues(
            "auth.group-membership.github.base-url=" + wireMock.baseUrl(),
            "auth.group-membership.github.access-token=ghp_test_token")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              assertTrue(context.getBeansOfType(HealthTrackable.class).isEmpty());
            });
  }

  @Test
  void shouldTrackGitHubInFiatHealthWhenEnabled() {
    contextRunner
        .withPropertyValues(
            "auth.group-membership.github.base-url=" + wireMock.baseUrl(),
            "auth.group-membership.github.access-token=ghp_test_token",
            "auth.group-membership.github.health-check-enabled=true")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              // Exactly one tracker, and not the provider itself, so disabling it is possible
              HealthTrackable healthTrackable = context.getBean(HealthTrackable.class);
              assertInstanceOf(GithubTeamsHealthTrackable.class, healthTrackable);
              assertSame(
                  context.getBean(GithubTeamsUserRolesProvider.class).getHealthTracker(),
                  healthTrackable.getHealthTracker());
            });
  }

  @Test
  void shouldTrackGitHubInFiatHealthWhateverTheCaseOfTheServiceName() {
    // The provider and GitHubConfig match auth.group-membership.service case-insensitively
    contextRunner
        .withPropertyValues(
            "auth.group-membership.service=GitHub",
            "auth.group-membership.github.base-url=" + wireMock.baseUrl(),
            "auth.group-membership.github.access-token=ghp_test_token",
            "auth.group-membership.github.health-check-enabled=true")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              assertInstanceOf(
                  GithubTeamsHealthTrackable.class, context.getBean(HealthTrackable.class));
            });
  }

  @Test
  void shouldMintANewInstallationTokenAfterGitHubRejectsTheCachedOne() {
    stubGitHubAppTokenExchange();
    Path privateKey = GitHubAppTestKeys.writePkcs8Pem(tempDir.resolve("key.pem"));
    // The first request is rejected, as after the app was suspended and reinstated
    wireMock.stubFor(
        get(urlPathEqualTo("/orgs/test-org"))
            .inScenario("revoked token")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\": \"Bad credentials\"}"))
            .willSetStateTo("accepted"));
    wireMock.stubFor(
        get(urlPathEqualTo("/orgs/test-org"))
            .inScenario("revoked token")
            .whenScenarioStateIs("accepted")
            .willReturn(okJson("{\"login\": \"test-org\", \"id\": 1}")));
    wireMock.stubFor(get(urlPathEqualTo("/orgs/test-org/members")).willReturn(okJson("[]")));
    wireMock.stubFor(get(urlPathEqualTo("/rate_limit")).willReturn(okJson("{\"resources\": {}}")));

    contextRunner
        .withPropertyValues(
            "auth.group-membership.github.base-url=" + wireMock.baseUrl(),
            "auth.group-membership.github.app-id=12345",
            "auth.group-membership.github.private-key-path=" + privateKey,
            "auth.group-membership.github.installation-id=" + INSTALLATION_ID)
        .run(
            context -> {
              GithubTeamsUserRolesProvider provider =
                  context.getBean(GithubTeamsUserRolesProvider.class);
              ExternalUser user = new ExternalUser().setId("alice");

              assertThrows(ProviderException.class, () -> provider.loadRoles(user));
              assertTrue(provider.loadRoles(user).isEmpty());

              // One token at startup, and a new one after GitHub rejected it
              wireMock.verify(
                  2,
                  postRequestedFor(
                      urlPathEqualTo("/app/installations/" + INSTALLATION_ID + "/access_tokens")));
            });
  }

  private void stubGitHubAppTokenExchange() {
    wireMock.stubFor(
        get(urlPathEqualTo("/app")).willReturn(okJson("{\"id\": 12345, \"name\": \"test-app\"}")));
    wireMock.stubFor(
        get(urlPathEqualTo("/app/installations/" + INSTALLATION_ID))
            .willReturn(
                okJson(
                    "{\"id\": "
                        + INSTALLATION_ID
                        + ", \"app_id\": 12345, \"account\": {\"login\": \"test-org\"}}")));
    wireMock.stubFor(
        post(urlPathEqualTo("/app/installations/" + INSTALLATION_ID + "/access_tokens"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"token\": \"ghs_test\", \"expires_at\": \"2099-01-01T00:00:00Z\"}")));
  }
}
