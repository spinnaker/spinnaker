/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.kork.github;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.netflix.spinnaker.kork.github.test.GitHubAppTestKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests of the GitHub App token exchange in {@link GitHubAppAuthenticator} against a
 * stubbed GitHub API: JWT generation, installation token creation, caching and refresh.
 */
class GitHubAppAuthenticatorTokenExchangeTest {

  private static final String APP_ID = "12345";
  private static final String INSTALLATION_ID = "67890";
  private static final String ACCESS_TOKENS_PATH =
      "/app/installations/" + INSTALLATION_ID + "/access_tokens";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private WireMockServer server;
  private Path privateKeyFile;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();

    privateKeyFile = GitHubAppTestKeys.writePkcs8Pem(tempDir.resolve("gh-app-key.pem"));

    server.stubFor(
        get(urlPathEqualTo("/app"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\": " + APP_ID + ", \"name\": \"test-app\"}")));
    server.stubFor(
        get(urlPathEqualTo("/app/installations/" + INSTALLATION_ID))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\": "
                            + INSTALLATION_ID
                            + ", \"app_id\": "
                            + APP_ID
                            + ", \"account\": {\"login\": \"test-org\"}}")));
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void shouldExchangeJwtForInstallationToken() throws Exception {
    stubAccessTokenCreation("ghs_stubbed_token", "2099-01-01T00:00:00Z");
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());

    assertEquals("ghs_stubbed_token", authenticator.getInstallationToken());

    // The token was created with a JWT whose payload matches GitHub's app authentication contract
    server.verify(
        postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH))
            .withHeader("Authorization", matching("Bearer eyJ.*")));
    String authorization =
        server
            .findAll(postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH)))
            .get(0)
            .getHeader("Authorization");
    JsonNode jwtPayload = decodeJwtPayload(authorization.substring("Bearer ".length()));
    assertEquals(APP_ID, jwtPayload.get("iss").asText());
    long lifetimeSeconds = jwtPayload.get("exp").asLong() - jwtPayload.get("iat").asLong();
    assertEquals(600, lifetimeSeconds);
  }

  @Test
  void shouldNormalizeApiBaseUrlWithTrailingSlash() throws Exception {
    stubAccessTokenCreation("ghs_stubbed_token", "2099-01-01T00:00:00Z");
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl() + "/");

    assertEquals("ghs_stubbed_token", authenticator.getInstallationToken());
  }

  @Test
  void shouldCacheInstallationTokenAcrossCalls() throws Exception {
    stubAccessTokenCreation("ghs_stubbed_token", "2099-01-01T00:00:00Z");
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());

    authenticator.getInstallationToken();
    authenticator.getInstallationToken();
    authenticator.getInstallationToken();

    // A cached token must be served without contacting the GitHub API at all
    server.verify(1, postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH)));
    server.verify(1, getRequestedFor(urlPathEqualTo("/app")));
    server.verify(1, getRequestedFor(urlPathEqualTo("/app/installations/" + INSTALLATION_ID)));
  }

  @Test
  void shouldRefreshTokenThatIsExpiredOrWithinTheRefreshBuffer() throws Exception {
    // Any timestamp closer than the 5-minute refresh buffer counts as expired
    stubAccessTokenCreation("ghs_expired_token", "2000-01-01T00:00:00Z");
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());

    authenticator.getInstallationToken();
    authenticator.getInstallationToken();

    server.verify(2, postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH)));
  }

  @Test
  void shouldDeriveInstallationFromRepository() throws Exception {
    stubRepoInstallation("test-org", "my-repo", "99999", "ghs_org_token");
    GitHubAppAuthenticator authenticator = deriveModeAuthenticator(server.baseUrl());

    assertEquals("ghs_org_token", authenticator.getInstallationTokenForRepo("test-org", "my-repo"));

    server.verify(1, getRequestedFor(urlPathEqualTo("/repos/test-org/my-repo/installation")));
    server.verify(1, postRequestedFor(urlPathEqualTo("/app/installations/99999/access_tokens")));
  }

  @Test
  void shouldServeCachedDerivedTokenWithoutAnyApiCalls() throws Exception {
    stubRepoInstallation("test-org", "my-repo", "99999", "ghs_org_token");
    GitHubAppAuthenticator authenticator = deriveModeAuthenticator(server.baseUrl());

    // warm the cache, then reset the journal so we count only post-warm-up traffic
    authenticator.getInstallationTokenForRepo("test-org", "my-repo");
    server.resetRequests();

    for (int i = 0; i < 5; i++) {
      assertEquals(
          "ghs_org_token", authenticator.getInstallationTokenForRepo("test-org", "my-repo"));
    }
    // a different repository of the same owner shares the owner's installation token
    assertEquals(
        "ghs_org_token", authenticator.getInstallationTokenForRepo("test-org", "another-repo"));

    assertEquals(
        0,
        server.findAll(anyRequestedFor(anyUrl())).size(),
        "cached installation tokens must be served without contacting the GitHub API");
  }

  @Test
  void shouldCacheTokensPerDerivedInstallation() throws Exception {
    stubRepoInstallation("test-org", "my-repo", "99999", "ghs_org_token");
    stubRepoInstallation("other-org", "other-repo", "88888", "ghs_other_org_token");
    GitHubAppAuthenticator authenticator = deriveModeAuthenticator(server.baseUrl());

    for (int i = 0; i < 2; i++) {
      assertEquals(
          "ghs_org_token", authenticator.getInstallationTokenForRepo("test-org", "my-repo"));
      assertEquals(
          "ghs_other_org_token",
          authenticator.getInstallationTokenForRepo("other-org", "other-repo"));
    }

    // one installation resolution and one token creation per owner
    server.verify(1, getRequestedFor(urlPathEqualTo("/repos/test-org/my-repo/installation")));
    server.verify(1, getRequestedFor(urlPathEqualTo("/repos/other-org/other-repo/installation")));
    server.verify(1, postRequestedFor(urlPathEqualTo("/app/installations/99999/access_tokens")));
    server.verify(1, postRequestedFor(urlPathEqualTo("/app/installations/88888/access_tokens")));
  }

  @Test
  void shouldResolveInstallationForUserOwnedRepositories() throws Exception {
    // user accounts are resolved through the same repository endpoint as organizations
    stubRepoInstallation("some-user", "personal-repo", "77777", "ghs_user_token");
    GitHubAppAuthenticator authenticator = deriveModeAuthenticator(server.baseUrl());

    assertEquals(
        "ghs_user_token", authenticator.getInstallationTokenForRepo("some-user", "personal-repo"));
  }

  @Test
  void shouldFailWithClearErrorWhenNoInstallationHasAccessToRepository() {
    server.stubFor(
        get(urlPathEqualTo("/repos/unknown-org/unknown-repo/installation"))
            .willReturn(aResponse().withStatus(404)));
    GitHubAppAuthenticator authenticator = deriveModeAuthenticator(server.baseUrl());

    IOException exception =
        assertThrows(
            IOException.class,
            () -> authenticator.getInstallationTokenForRepo("unknown-org", "unknown-repo"));

    assertTrue(
        exception
            .getMessage()
            .contains("has no installation with access to 'unknown-org/unknown-repo'"),
        exception.getMessage());
  }

  @Test
  void shouldReportAppAuthenticationFailuresDistinctlyFromMissingInstallations() {
    // /app failing means the app credentials or endpoint are wrong - not a missing installation
    server.stubFor(get(urlPathEqualTo("/app")).willReturn(aResponse().withStatus(401)));
    GitHubAppAuthenticator authenticator = deriveModeAuthenticator(server.baseUrl());

    IOException exception =
        assertThrows(
            IOException.class,
            () -> authenticator.getInstallationTokenForRepo("test-org", "my-repo"));

    assertTrue(
        exception.getMessage().contains("Failed to authenticate as GitHub App"),
        exception.getMessage());
    assertFalse(exception.getMessage().contains("has no installation with access"));
  }

  @Test
  void shouldMintOneTokenPerOwnerUnderConcurrentAccess() throws Exception {
    stubRepoInstallation("test-org", "my-repo", "99999", "ghs_org_token");
    GitHubAppAuthenticator authenticator = deriveModeAuthenticator(server.baseUrl());

    int threads = 8;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      CountDownLatch startLine = new CountDownLatch(1);
      List<Future<String>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        results.add(
            executor.submit(
                () -> {
                  startLine.await();
                  return authenticator.getInstallationTokenForRepo("test-org", "my-repo");
                }));
      }
      startLine.countDown();
      for (Future<String> result : results) {
        assertEquals("ghs_org_token", result.get(30, TimeUnit.SECONDS));
      }
    } finally {
      executor.shutdownNow();
    }

    server.verify(1, postRequestedFor(urlPathEqualTo("/app/installations/99999/access_tokens")));
    server.verify(1, getRequestedFor(urlPathEqualTo("/repos/test-org/my-repo/installation")));
  }

  @Test
  void shouldAllowOwnersOnTheOrganizationAllowlist() throws Exception {
    stubRepoInstallation("test-org", "my-repo", "99999", "ghs_org_token");
    GitHubAppAuthenticator authenticator = restrictedAuthenticator(Set.of("test-org", "other-org"));

    assertEquals("ghs_org_token", authenticator.getInstallationTokenForRepo("test-org", "my-repo"));
  }

  @Test
  void shouldRejectOwnersOutsideTheOrganizationAllowlistWithoutAnyApiCall() {
    stubRepoInstallation("forbidden-org", "my-repo", "99999", "ghs_org_token");
    GitHubAppAuthenticator authenticator = restrictedAuthenticator(Set.of("test-org"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> authenticator.getInstallationTokenForRepo("forbidden-org", "my-repo"));

    assertTrue(
        exception.getMessage().contains("not permitted to access repositories owned by"),
        exception.getMessage());
    assertTrue(exception.getMessage().contains("forbidden-org"), exception.getMessage());
    assertTrue(exception.getMessage().contains("[test-org]"), exception.getMessage());
    assertEquals(
        0,
        server.findAll(anyRequestedFor(anyUrl())).size(),
        "a rejected owner must not reach the GitHub API");
  }

  @Test
  void shouldMatchTheOrganizationAllowlistCaseInsensitively() throws Exception {
    stubRepoInstallation("Test-Org", "my-repo", "99999", "ghs_org_token");
    GitHubAppAuthenticator authenticator = restrictedAuthenticator(Set.of("test-org"));

    assertEquals("ghs_org_token", authenticator.getInstallationTokenForRepo("Test-Org", "my-repo"));
  }

  @Test
  void shouldAllowAnyOwnerWhenAllowlistIsEmpty() throws Exception {
    stubRepoInstallation("any-org", "my-repo", "99999", "ghs_org_token");
    GitHubAppAuthenticator authenticator = restrictedAuthenticator(Set.of());

    assertEquals("ghs_org_token", authenticator.getInstallationTokenForRepo("any-org", "my-repo"));
  }

  @Test
  void shouldIgnoreTheOrganizationAllowlistInPinnedMode() throws Exception {
    stubAccessTokenCreation("ghs_stubbed_token", "2099-01-01T00:00:00Z");
    GitHubAppAuthenticator authenticator =
        new GitHubAppAuthenticator(
            APP_ID,
            privateKeyFile.toString(),
            INSTALLATION_ID,
            server.baseUrl(),
            Set.of("some-other-org"));

    assertEquals("ghs_stubbed_token", authenticator.getInstallationToken());
  }

  @Test
  void shouldServeManyOwnersCorrectlyDespiteLockStriping() throws Exception {
    // more owners than lock stripes, so several owners necessarily share a lock
    int owners = 50;
    for (int i = 0; i < owners; i++) {
      stubRepoInstallation("org-" + i, "my-repo", String.valueOf(10000 + i), "ghs_token_" + i);
    }
    GitHubAppAuthenticator authenticator = deriveModeAuthenticator(server.baseUrl());

    for (int i = 0; i < owners; i++) {
      assertEquals(
          "ghs_token_" + i, authenticator.getInstallationTokenForRepo("org-" + i, "my-repo"));
    }
    // and every owner is still cached afterwards
    server.resetRequests();
    for (int i = 0; i < owners; i++) {
      assertEquals(
          "ghs_token_" + i, authenticator.getInstallationTokenForRepo("org-" + i, "my-repo"));
    }
    assertEquals(0, server.findAll(anyRequestedFor(anyUrl())).size());
  }

  @Test
  void pinnedGetInstallationTokenFailsInDeriveMode() {
    GitHubAppAuthenticator authenticator = deriveModeAuthenticator(server.baseUrl());

    assertThrows(IllegalStateException.class, authenticator::getInstallationToken);
  }

  private GitHubAppAuthenticator deriveModeAuthenticator(String baseUrl) {
    return new GitHubAppAuthenticator(APP_ID, privateKeyFile.toString(), null, baseUrl);
  }

  private GitHubAppAuthenticator restrictedAuthenticator(Set<String> allowedOrganizations) {
    return new GitHubAppAuthenticator(
        APP_ID, privateKeyFile.toString(), null, server.baseUrl(), allowedOrganizations);
  }

  private void stubRepoInstallation(
      String owner, String repo, String installationId, String token) {
    String installationJson =
        "{\"id\": "
            + installationId
            + ", \"app_id\": "
            + APP_ID
            + ", \"account\": {\"login\": \""
            + owner
            + "\"}}";
    server.stubFor(
        get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/installation"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(installationJson)));
    server.stubFor(
        post(urlPathEqualTo("/app/installations/" + installationId + "/access_tokens"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"token\": \""
                            + token
                            + "\", \"expires_at\": \"2099-01-01T00:00:00Z\"}")));
  }

  private GitHubAppAuthenticator authenticator(String baseUrl) {
    return new GitHubAppAuthenticator(APP_ID, privateKeyFile.toString(), INSTALLATION_ID, baseUrl);
  }

  private void stubAccessTokenCreation(String token, String expiresAt) {
    server.stubFor(
        post(urlPathEqualTo(ACCESS_TOKENS_PATH))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"token\": \"" + token + "\", \"expires_at\": \"" + expiresAt + "\"}")));
  }

  private JsonNode decodeJwtPayload(String jwt) throws Exception {
    String payloadSegment = jwt.split("\\.")[1];
    byte[] decoded = Base64.getUrlDecoder().decode(payloadSegment);
    return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
  }
}
