/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.roles.github.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class GitHubAppAuthServiceTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private GitHubAppAuthService authService;
  private String testAppId = "12345";
  private String testInstallationId = "67890";
  private Path tempPrivateKeyFile;
  private OkHttpClient httpClient;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    // Generate a test RSA private key
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    RSAPrivateKey privateKey = (RSAPrivateKey) keyGen.generateKeyPair().getPrivate();

    // Create PEM format private key
    String pemKey =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(privateKey.getEncoded())
            + "\n"
            + "-----END PRIVATE KEY-----";

    tempPrivateKeyFile = tempDir.resolve("test-private-key.pem");
    Files.write(tempPrivateKeyFile, pemKey.getBytes());

    httpClient = new OkHttpClient();
    authService =
        new GitHubAppAuthService(
            testAppId,
            tempPrivateKeyFile.toString(),
            testInstallationId,
            wireMock.baseUrl(),
            httpClient);
  }

  @Test
  void shouldGenerateInstallationToken() throws IOException {
    // Given
    String mockTokenResponse =
        "{\"token\":\"ghs_test_token\",\"expires_at\":\""
            + Instant.now().plusSeconds(3600).toString()
            + "\"}";

    wireMock.stubFor(
        post(urlEqualTo("/app/installations/" + testInstallationId + "/access_tokens"))
            .willReturn(aResponse().withStatus(200).withBody(mockTokenResponse)));

    // When
    String token = authService.getInstallationToken();

    // Then
    assertEquals("ghs_test_token", token);

    // Verify the request was made correctly
    wireMock.verify(
        postRequestedFor(urlEqualTo("/app/installations/" + testInstallationId + "/access_tokens"))
            .withHeader("Authorization", matching("Bearer .*"))
            .withHeader("Accept", equalTo("application/vnd.github.v3+json"))
            .withHeader("User-Agent", equalTo("Spinnaker-Fiat")));
  }

  @Test
  void shouldCacheInstallationToken() throws IOException {
    // Given
    String futureExpiry = Instant.now().plusSeconds(3600).toString();
    String mockTokenResponse =
        "{\"token\":\"ghs_cached_token\",\"expires_at\":\"" + futureExpiry + "\"}";

    wireMock.stubFor(
        post(urlEqualTo("/app/installations/" + testInstallationId + "/access_tokens"))
            .willReturn(aResponse().withStatus(200).withBody(mockTokenResponse)));

    // When
    String token1 = authService.getInstallationToken();
    String token2 = authService.getInstallationToken();

    // Then
    assertEquals("ghs_cached_token", token1);
    assertEquals("ghs_cached_token", token2);
    wireMock.verify(
        1,
        postRequestedFor(
            urlEqualTo(
                "/app/installations/"
                    + testInstallationId
                    + "/access_tokens"))); // Only called once due to caching
  }

  @Test
  void shouldRefreshExpiredToken() throws IOException {
    // Given - expired token
    String expiredTime = Instant.now().minusSeconds(3600).toString();
    String expiredTokenResponse =
        "{\"token\":\"ghs_expired_token\",\"expires_at\":\"" + expiredTime + "\"}";

    // Fresh token
    String futureTime = Instant.now().plusSeconds(3600).toString();
    String freshTokenResponse =
        "{\"token\":\"ghs_fresh_token\",\"expires_at\":\"" + futureTime + "\"}";

    wireMock.stubFor(
        post(urlEqualTo("/app/installations/" + testInstallationId + "/access_tokens"))
            .inScenario("Token Refresh")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(200).withBody(expiredTokenResponse))
            .willSetStateTo("Expired Token Returned"));

    wireMock.stubFor(
        post(urlEqualTo("/app/installations/" + testInstallationId + "/access_tokens"))
            .inScenario("Token Refresh")
            .whenScenarioStateIs("Expired Token Returned")
            .willReturn(aResponse().withStatus(200).withBody(freshTokenResponse)));

    // When
    String token1 = authService.getInstallationToken(); // Gets expired token
    String token2 = authService.getInstallationToken(); // Should refresh and get fresh token

    // Then
    assertEquals("ghs_expired_token", token1);
    assertEquals("ghs_fresh_token", token2);
    wireMock.verify(
        2,
        postRequestedFor(
            urlEqualTo(
                "/app/installations/"
                    + testInstallationId
                    + "/access_tokens"))); // Called twice due to refresh
  }

  @Test
  void shouldHandleTokenRequestFailure() {
    // Given
    wireMock.stubFor(
        post(urlEqualTo("/app/installations/" + testInstallationId + "/access_tokens"))
            .willReturn(aResponse().withStatus(401).withBody("{\"message\":\"Bad credentials\"}")));

    // When & Then
    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              authService.getInstallationToken();
            });

    assertTrue(exception.getMessage().contains("Failed to get installation token"));
    assertTrue(exception.getMessage().contains("401"));
  }

  @Test
  void shouldHandleInvalidPrivateKeyFile() {
    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              new GitHubAppAuthService(
                  testAppId,
                  "/nonexistent/path/private-key.pem",
                  testInstallationId,
                  "https://api.github.com",
                  httpClient);
            });

    assertTrue(exception.getMessage().contains("Failed to load GitHub App private key"));
  }

  @Test
  void shouldHandleInvalidTokenResponse() {
    // Given - invalid JSON response
    wireMock.stubFor(
        post(urlEqualTo("/app/installations/" + testInstallationId + "/access_tokens"))
            .willReturn(aResponse().withStatus(200).withBody("invalid json")));

    // When & Then
    assertThrows(
        Exception.class,
        () -> {
          authService.getInstallationToken();
        });
  }

  @Test
  void shouldRefreshTokenBeforeExpiry() throws IOException {
    // Given - token expiring soon (within refresh buffer)
    String soonToExpire =
        Instant.now().plusSeconds(240).toString(); // 4 minutes from now (< 5 min buffer)
    String expiringTokenResponse =
        "{\"token\":\"ghs_expiring_token\",\"expires_at\":\"" + soonToExpire + "\"}";

    String futureTime = Instant.now().plusSeconds(3600).toString();
    String freshTokenResponse =
        "{\"token\":\"ghs_refreshed_token\",\"expires_at\":\"" + futureTime + "\"}";

    wireMock.stubFor(
        post(urlEqualTo("/app/installations/" + testInstallationId + "/access_tokens"))
            .inScenario("Token Expiry Buffer")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(200).withBody(expiringTokenResponse))
            .willSetStateTo("Expiring Token Returned"));

    wireMock.stubFor(
        post(urlEqualTo("/app/installations/" + testInstallationId + "/access_tokens"))
            .inScenario("Token Expiry Buffer")
            .whenScenarioStateIs("Expiring Token Returned")
            .willReturn(aResponse().withStatus(200).withBody(freshTokenResponse)));

    // When
    String token1 = authService.getInstallationToken(); // Gets expiring token
    String token2 = authService.getInstallationToken(); // Should refresh due to expiry buffer

    // Then
    assertEquals("ghs_expiring_token", token1);
    assertEquals("ghs_refreshed_token", token2);
    wireMock.verify(
        2,
        postRequestedFor(
            urlEqualTo("/app/installations/" + testInstallationId + "/access_tokens")));
  }
}
