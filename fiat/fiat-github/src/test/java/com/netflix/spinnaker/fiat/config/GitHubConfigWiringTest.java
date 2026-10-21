/*
 * Copyright 2026 spinnaker.io.
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
import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
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
