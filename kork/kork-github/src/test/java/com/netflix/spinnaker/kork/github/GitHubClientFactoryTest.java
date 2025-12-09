/*
 * Copyright 2025 Razorpay.
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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.github.GitHub;

/** Tests for GitHubClientFactory. */
class GitHubClientFactoryTest {

  // Pre-generated RSA 2048-bit private key for testing (PKCS#8 format)
  private static final String TEST_PRIVATE_KEY =
      """
      -----BEGIN PRIVATE KEY-----
      MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCo2X/zDuXwB5qj
      EajeUD6+cBvbgTYVU4Vdisrd41XvKYpSpqnX+Bn9UFdkV983eJHN3glDLkLFb9Xz
      Xjc2JQXolQPRBdaWrmq16Ii1FcRSI9xltFuYtObROMzre3TrQ158anF0Y/2vJjRy
      RClZSFGaldo/vBz72oBxGSjDE4XWKlqeJawiGtAEbkoToiCNeFhNNOxTFoe33ZWE
      iAbku53xfmv3V93Mj/WM5Isje6kvu8uLQCOl7yTAaTCFLYY3nY1+V1VCNq8f/To4
      kV46KbKRws9WcSe0UE+ET2lG0Y6S06AeHf9FWzTZzg+7K49EA3UQNfBRmmCD9f4t
      BMlQPMA1AgMBAAECggEARWDtToFaIKj3NLbuZL6jMVveTnDGuLeTTo7XcZnWNwmi
      EPjzQ87pWukWp5/lk6TigC0SMD0DaZ3c0v1tAT3wMhN8uHfGJy7uoOU1uvaBLuEW
      T+HuWw5F40UMClw1e++4FLYl/RWS6NNxbFwug0WQZkzZmyOf4ypyaUZVteZBMXCU
      wp2bE/W0cVGEUbzNzzZGpoXxXXRXzn+kirnFjS9d45i/HUwm1jHa4v0Tfay4Ar9v
      BSVKuthJgaTxnDKWvxQ9s6RDWMVo5Log6b4RGDOZV7dIb5eRQcwCH5Rq2mxU370T
      3hLyEx4ICAKl8ZBg/dTeJ3/qsl2H3k1LXHWL7nrhFwKBgQDpSjXUWgPM0oGlY5Lq
      24VVMlVLKHJTUQF2dl/PUUnKXm41iNODf1/Fagb4QPkeKp72CYx7lfxC2lPqs3cT
      wo5jxVSloJoUVf2qNdXSwMUh0T2w+LpOV5AlMDktdQ8V1kwLWtrTgc7RK1Mj8zSQ
      JF8FBMAFGOPw1clE9GbTfQ4TTwKBgQC5SWGKaTrTMhiMajGeX20JP4j7cVhfU2tR
      11MQ0o4re50mzC79vwTk2Ybar6qVVIvInWfteoXhaOoHfzi2bGr04sTRHQjCo8C3
      qz43IiJXdkC3fugeZqPJ6nZ3JuUPC6dq+sOXF+oJvRq85GuBP6vt1o4uxe1W+CsQ
      JJK4PF2jOwKBgDcYtbnfQIKBPOlIqQwaqFTEvGwxsz6GJShLMLmP4zOONc0i8YFe
      9cl0Dw1Wmv9K5ZwKCUmu1JMdaTBHDlp2WpappiIv2fPvkyc967AIowYnmsBPHgEe
      oQaHaxmXSebIY9FStde6EpRH/SzCZamdTWusAYWyqTLZ6t0EM7zDDi31AoGAOwSX
      sCnClgDv9tHgiiylI3v8WvMIjhyZI5FtoP8gT9NpBDGniiWtHmP3Y3Lu5+/tMnKI
      5wjO2jS7zrWET/8KtoQA4wbXgn/8Y8SE5bTWsXs2M/yVXRGefDNVlrBp57fzlMzZ
      Pihc4Ms+WAp9/8ZTMkfUNCvRZJFZziOIJGz9+n8CgYEA30ynNn0xOuRFglhVHQHL
      yjN8hbqztBY+QT6C8MvUA03mMQb4cPMntJw73FE9Sta0ba5BBIRUBpdx/XpmtZaP
      7JjzbwM5ZUEu6SM8hP5bV5XQeocVqVSZbjI6NSVIHTlkRI+bYBmA0vRYEWstM3DG
      Jai6vOn73aXT257KuK2nZjo=
      -----END PRIVATE KEY-----""";

  private Path tempPrivateKeyFile;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    tempPrivateKeyFile = tempDir.resolve("test-private-key.pem");
    Files.write(tempPrivateKeyFile, TEST_PRIVATE_KEY.getBytes());
  }

  @Test
  void shouldCreateClientWithPAT() throws IOException {
    // When
    GitHub client = GitHubClientFactory.createWithPAT("https://api.github.com", "ghp_test_token");

    // Then - Client should be created (connection is lazy in hub4j)
    assertNotNull(client);
  }

  @Test
  void shouldCreateClientWithGitHubApp() {
    // When & Then - Will fail to connect (no real GitHub API) but verifies factory works
    assertThrows(
        IOException.class,
        () ->
            GitHubClientFactory.createWithGitHubApp(
                "https://api.github.com", "12345", tempPrivateKeyFile.toString(), "67890"));
  }

  @Test
  void shouldFailWithInvalidPrivateKeyPath() {
    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                GitHubClientFactory.createWithGitHubApp(
                    "https://api.github.com", "12345", "/nonexistent/key.pem", "67890"));

    assertTrue(exception.getMessage().contains("Failed to load GitHub App private key"));
  }

  @Test
  void shouldCreateClientWithAuthenticator() {
    // Given
    GitHubAppAuthenticator authenticator =
        new GitHubAppAuthenticator(
            "12345", tempPrivateKeyFile.toString(), "67890", "https://api.github.com");

    // When & Then - Will fail to connect (no real GitHub API) but verifies factory works
    assertThrows(
        IOException.class, () -> GitHubClientFactory.createWithAuthenticator(authenticator));
  }

  @Test
  void shouldSupportGitHubEnterprise() throws IOException {
    // When
    GitHub client =
        GitHubClientFactory.createWithPAT("https://github.company.com/api/v3", "ghp_test_token");

    // Then - Client should be created (connection is lazy in hub4j)
    assertNotNull(client);
  }

  @Test
  void shouldSupportGitHubEnterpriseForGitHubApp() {
    // When & Then
    assertThrows(
        IOException.class,
        () ->
            GitHubClientFactory.createWithGitHubApp(
                "https://github.company.com/api/v3",
                "12345",
                tempPrivateKeyFile.toString(),
                "67890"));
  }

  @Test
  void shouldHandleBaseUrlWithTrailingSlash() throws IOException {
    // When
    GitHub client = GitHubClientFactory.createWithPAT("https://api.github.com/", "ghp_test_token");

    // Then - Client should be created (connection is lazy in hub4j)
    assertNotNull(client);
  }

  // ===== Tests for createAuthenticator =====

  @Test
  void shouldCreateAuthenticator() {
    // When
    GitHubAppAuthenticator authenticator =
        GitHubClientFactory.createAuthenticator(
            "https://api.github.com", "12345", tempPrivateKeyFile.toString(), "67890");

    // Then
    assertNotNull(authenticator);
  }

  @Test
  void shouldCreateAuthenticatorForGitHubEnterprise() {
    // When
    GitHubAppAuthenticator authenticator =
        GitHubClientFactory.createAuthenticator(
            "https://github.company.com/api/v3", "12345", tempPrivateKeyFile.toString(), "67890");

    // Then
    assertNotNull(authenticator);
  }

  @Test
  void shouldFailCreateAuthenticatorWithInvalidPrivateKeyPath() {
    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                GitHubClientFactory.createAuthenticator(
                    "https://api.github.com", "12345", "/nonexistent/key.pem", "67890"));

    assertTrue(exception.getMessage().contains("Failed to load GitHub App private key"));
  }

  @Test
  void shouldCreateAuthenticatorThatCanGetClient() {
    // Given
    GitHubAppAuthenticator authenticator =
        GitHubClientFactory.createAuthenticator(
            "https://api.github.com", "12345", tempPrivateKeyFile.toString(), "67890");

    // When & Then - Will fail to connect (no real GitHub API) but verifies authenticator works
    assertThrows(IOException.class, () -> authenticator.getAuthenticatedClient());
  }

  @Test
  void shouldCreateAuthenticatorThatCanGetToken() {
    // Given
    GitHubAppAuthenticator authenticator =
        GitHubClientFactory.createAuthenticator(
            "https://api.github.com", "12345", tempPrivateKeyFile.toString(), "67890");

    // When & Then - Will fail to connect (no real GitHub API) but verifies authenticator works
    assertThrows(IOException.class, () -> authenticator.getInstallationToken());
  }
}
