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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
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
