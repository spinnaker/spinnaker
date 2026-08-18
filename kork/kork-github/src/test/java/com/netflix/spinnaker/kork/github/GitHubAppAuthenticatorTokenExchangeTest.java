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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
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

    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    String pkcs8Pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(keyPairGenerator.generateKeyPair().getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
    privateKeyFile = tempDir.resolve("gh-app-key.pem");
    Files.writeString(privateKeyFile, pkcs8Pem);

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

    server.verify(1, postRequestedFor(urlPathEqualTo(ACCESS_TOKENS_PATH)));
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
