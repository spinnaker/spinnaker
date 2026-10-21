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
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.netflix.spinnaker.kork.github.test.GitHubAppTestKeys;
import com.netflix.spinnaker.kork.test.time.MutableClock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.github.GitHub;

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
  void authenticatedClientShouldRenewItsTokenWhenTheCachedOneExpires() throws Exception {
    // The first token is already inside the refresh buffer, standing in for a client that has
    // been held for an hour (e.g. a singleton @Bean); the second one is long-lived.
    server.stubFor(
        post(urlPathEqualTo(ACCESS_TOKENS_PATH))
            .inScenario("token rotation")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(accessTokenResponse("ghs_first_token", "2000-01-01T00:00:00Z"))
            .willSetStateTo("rotated"));
    server.stubFor(
        post(urlPathEqualTo(ACCESS_TOKENS_PATH))
            .inScenario("token rotation")
            .whenScenarioStateIs("rotated")
            .willReturn(accessTokenResponse("ghs_second_token", "2099-01-01T00:00:00Z")));
    stubOrganizations();
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());

    GitHub client = authenticator.getAuthenticatedClient();
    client.getOrganization("org-a");
    client.getOrganization("org-b");

    // The same client instance picked up the renewed token instead of reusing the expired one
    assertEquals(
        List.of("token ghs_second_token", "token ghs_second_token"),
        organizationRequestAuthorizations());
    server.verify(2, postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH)));
  }

  @Test
  void authenticatedClientShouldServeCachedTokenWithoutMintingPerRequest() throws Exception {
    stubAccessTokenCreation("ghs_stubbed_token", "2099-01-01T00:00:00Z");
    stubOrganizations();
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());

    GitHub client = authenticator.getAuthenticatedClient();
    client.getOrganization("org-a");
    client.getOrganization("org-b");
    client.getOrganization("org-c");

    assertEquals(
        List.of("token ghs_stubbed_token", "token ghs_stubbed_token", "token ghs_stubbed_token"),
        organizationRequestAuthorizations());
    // One mint, made eagerly by getAuthenticatedClient; requests are served from the cache
    server.verify(1, postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH)));
  }

  @Test
  void authenticatedClientShouldFailFastWhenNoTokenCanBeMinted() {
    server.stubFor(
        post(urlPathEqualTo(ACCESS_TOKENS_PATH))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\": \"Bad credentials\"}")));
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());

    assertThrows(IOException.class, authenticator::getAuthenticatedClient);
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

  @Test
  void shouldBackdateJwtIssueTimeToTolerateClockSkew() throws Exception {
    stubAccessTokenCreation("ghs_stubbed_token", "2099-01-01T00:00:00Z");
    MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));

    authenticator(clock).getInstallationToken();

    // GitHub rejects a JWT issued in its future, or expiring more than 10 minutes after its now,
    // so a host clock that runs slightly ahead must still produce an acceptable JWT
    JsonNode jwtPayload = decodeJwtPayload(mintRequestJwts().get(0));
    long now = clock.instant().getEpochSecond();
    assertEquals(now - 60, jwtPayload.get("iat").asLong());
    assertEquals(now + 9 * 60, jwtPayload.get("exp").asLong());
  }

  @Test
  void shouldKeepServingTheCachedTokenWhenRenewalFailsBeforeItExpires() throws Exception {
    stubTokenThenMintFailure(ACCESS_TOKENS_PATH, "ghs_cached_token", "2030-01-01T01:00:00Z");
    MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
    GitHubAppAuthenticator authenticator = authenticator(clock);
    assertEquals("ghs_cached_token", authenticator.getInstallationToken());

    // Inside the renewal window the mint fails, but the cached token is still valid
    clock.instant(Instant.parse("2030-01-01T00:57:00Z"));
    assertEquals("ghs_cached_token", authenticator.getInstallationToken());
    server.verify(2, postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH)));

    // Once the cached token has really expired, the failure surfaces
    clock.instant(Instant.parse("2030-01-01T01:00:01Z"));
    assertThrows(IOException.class, authenticator::getInstallationToken);
  }

  @Test
  void shouldNotRetryAFailedRenewalOnEveryCallWhileTheCachedTokenIsValid() throws Exception {
    stubTokenThenMintFailure(ACCESS_TOKENS_PATH, "ghs_cached_token", "2030-01-01T01:00:00Z");
    MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
    GitHubAppAuthenticator authenticator = authenticator(clock);
    authenticator.getInstallationToken();

    // The renewal fails inside the window; every request uses the token, so retrying on each call
    // would cost three GitHub calls (and a warning) per request while GitHub is failing
    clock.instant(Instant.parse("2030-01-01T00:57:00Z"));
    authenticator.getInstallationToken();
    authenticator.getInstallationToken();
    authenticator.getInstallationToken();
    server.verify(2, postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH)));

    // After a short pause, renewal is attempted again
    clock.instant(Instant.parse("2030-01-01T00:57:31Z"));
    assertEquals("ghs_cached_token", authenticator.getInstallationToken());
    server.verify(3, postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH)));
  }

  @Test
  void shouldKeepServingTheCachedDerivedTokenWhenRenewalFailsBeforeItExpires() throws Exception {
    stubRepoInstallation("test-org", "my-repo", "99999", "unused");
    stubTokenThenMintFailure(
        "/app/installations/99999/access_tokens", "ghs_org_token", "2030-01-01T01:00:00Z");
    MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
    GitHubAppAuthenticator authenticator =
        new GitHubAppAuthenticator(
            APP_ID, privateKeyFile.toString(), null, server.baseUrl(), Set.of(), clock);
    assertEquals("ghs_org_token", authenticator.getInstallationTokenForRepo("test-org", "my-repo"));

    clock.instant(Instant.parse("2030-01-01T00:57:00Z"));
    assertEquals("ghs_org_token", authenticator.getInstallationTokenForRepo("test-org", "my-repo"));

    clock.instant(Instant.parse("2030-01-01T01:00:01Z"));
    assertThrows(
        IOException.class, () -> authenticator.getInstallationTokenForRepo("test-org", "my-repo"));
  }

  @Test
  void shouldSignWithTheRotatedPrivateKeyAfterTheKeyFileChanges() throws Exception {
    // An already-expired token, so that every call mints and signs a new JWT
    stubAccessTokenCreation("ghs_expired_token", "2000-01-01T00:00:00Z");
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());
    PublicKey originalKey = publicKeyOf(privateKeyFile);
    authenticator.getInstallationToken();

    GitHubAppTestKeys.writePkcs8Pem(privateKeyFile);
    Files.setLastModifiedTime(privateKeyFile, FileTime.from(Instant.now().plusSeconds(60)));
    PublicKey rotatedKey = publicKeyOf(privateKeyFile);
    authenticator.getInstallationToken();

    List<String> jwts = mintRequestJwts();
    assertTrue(isSignedBy(jwts.get(0), originalKey), "first JWT signed with the original key");
    assertTrue(isSignedBy(jwts.get(1), rotatedKey), "second JWT signed with the rotated key");
  }

  @Test
  void shouldSignWithTheRotatedPrivateKeyEvenWhenTheFileKeepsItsModificationTime()
      throws Exception {
    // e.g. a key copied in with cp -p or extracted from an archive
    stubAccessTokenCreation("ghs_expired_token", "2000-01-01T00:00:00Z");
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());
    FileTime originalModifiedTime = Files.getLastModifiedTime(privateKeyFile);
    authenticator.getInstallationToken();

    GitHubAppTestKeys.writePkcs8Pem(privateKeyFile);
    Files.setLastModifiedTime(privateKeyFile, originalModifiedTime);
    PublicKey rotatedKey = publicKeyOf(privateKeyFile);
    authenticator.getInstallationToken();

    assertTrue(isSignedBy(mintRequestJwts().get(1), rotatedKey));
  }

  @Test
  void shouldNotTreatAnInterruptedRenewalAsAGitHubFailure() throws Exception {
    stubTokenThenMintFailure(ACCESS_TOKENS_PATH, "ghs_cached_token", "2030-01-01T01:00:00Z");
    MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
    GitHubAppAuthenticator authenticator = authenticator(clock);
    authenticator.getInstallationToken();
    clock.instant(Instant.parse("2030-01-01T00:57:00Z"));

    // The caller was cancelled (e.g. by a timeout) while the token was being renewed: it must
    // stop, rather than carry on with the cached token as if GitHub had failed
    Thread.currentThread().interrupt();
    try {
      assertThrows(IOException.class, authenticator::getInstallationToken);
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt must be preserved");
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void shouldKeepSigningWithThePreviousKeyWhenTheChangedKeyFileIsUnreadable() throws Exception {
    stubAccessTokenCreation("ghs_expired_token", "2000-01-01T00:00:00Z");
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());
    PublicKey originalKey = publicKeyOf(privateKeyFile);
    authenticator.getInstallationToken();

    // e.g. a secret volume caught mid-update
    Files.writeString(privateKeyFile, "not a key");
    Files.setLastModifiedTime(privateKeyFile, FileTime.from(Instant.now().plusSeconds(60)));

    assertEquals("ghs_expired_token", authenticator.getInstallationToken());
    assertTrue(isSignedBy(mintRequestJwts().get(1), originalKey));
  }

  @Test
  void shouldMintANewTokenAfterTheCachedOneIsInvalidated() throws Exception {
    stubAccessTokenCreation("ghs_stubbed_token", "2099-01-01T00:00:00Z");
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());
    authenticator.getInstallationToken();

    authenticator.invalidateInstallationToken();
    authenticator.getInstallationToken();

    server.verify(2, postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH)));
  }

  @Test
  void shouldReportTheCachedInstallationTokenExpiry() throws Exception {
    stubAccessTokenCreation("ghs_stubbed_token", "2099-01-01T00:00:00Z");
    GitHubAppAuthenticator authenticator = authenticator(server.baseUrl());
    assertNull(authenticator.getInstallationTokenExpiry());

    authenticator.getInstallationToken();

    assertEquals(Instant.parse("2099-01-01T00:00:00Z"), authenticator.getInstallationTokenExpiry());
  }

  private GitHubAppAuthenticator authenticator(Clock clock) {
    return new GitHubAppAuthenticator(
        APP_ID, privateKeyFile.toString(), INSTALLATION_ID, server.baseUrl(), Set.of(), clock);
  }

  /** Stubs one successful token creation at {@code path}, then failures for every later mint. */
  private void stubTokenThenMintFailure(String path, String token, String expiresAt) {
    server.stubFor(
        post(urlPathEqualTo(path))
            .inScenario("mint failure")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(accessTokenResponse(token, expiresAt))
            .willSetStateTo("failing"));
    server.stubFor(
        post(urlPathEqualTo(path))
            .inScenario("mint failure")
            .whenScenarioStateIs("failing")
            .willReturn(aResponse().withStatus(500)));
  }

  /** The JWTs sent to create installation tokens, in request order. */
  private List<String> mintRequestJwts() {
    return server.findAll(postRequestedFor(urlPathMatching("/app/installations/.*"))).stream()
        .map(request -> request.getHeader("Authorization").substring("Bearer ".length()))
        .collect(Collectors.toList());
  }

  private static PublicKey publicKeyOf(Path pemFile) throws Exception {
    String base64 =
        Files.readString(pemFile)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "");
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    RSAPrivateCrtKey privateKey =
        (RSAPrivateCrtKey)
            keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(base64)));
    return keyFactory.generatePublic(
        new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
  }

  private static boolean isSignedBy(String jwt, PublicKey key) throws Exception {
    int signatureStart = jwt.lastIndexOf('.');
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initVerify(key);
    signature.update(jwt.substring(0, signatureStart).getBytes(StandardCharsets.US_ASCII));
    return signature.verify(Base64.getUrlDecoder().decode(jwt.substring(signatureStart + 1)));
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
        post(urlPathEqualTo(ACCESS_TOKENS_PATH)).willReturn(accessTokenResponse(token, expiresAt)));
  }

  private ResponseDefinitionBuilder accessTokenResponse(String token, String expiresAt) {
    return aResponse()
        .withStatus(201)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"token\": \"" + token + "\", \"expires_at\": \"" + expiresAt + "\"}");
  }

  /**
   * Stubs GET /orgs/{org}. Tests use a distinct org per call because hub4j caches organizations per
   * client, and a cached lookup would not send a request.
   */
  private void stubOrganizations() {
    server.stubFor(
        get(urlPathMatching("/orgs/[^/]+"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"login\": \"test-org\", \"id\": 1}")));
  }

  private List<String> organizationRequestAuthorizations() {
    return server.findAll(getRequestedFor(urlPathMatching("/orgs/[^/]+"))).stream()
        .map(request -> request.getHeader("Authorization"))
        .collect(Collectors.toList());
  }

  private JsonNode decodeJwtPayload(String jwt) throws Exception {
    String payloadSegment = jwt.split("\\.")[1];
    byte[] decoded = Base64.getUrlDecoder().decode(payloadSegment);
    return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
  }
}
