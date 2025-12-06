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
      "-----BEGIN PRIVATE KEY-----\n"
          + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDLCqJMBNUnCPC5\n"
          + "ZCDLVa0h9c/N87e5AUdO7E7UwZmJN7+VRe7j6uSySeS9Bn1PmtZSETXztz8ph/iV\n"
          + "e4y+iBSqtvZzSkvLC5ZgzsD37v+Yw/hFSQu/NZgcd5wcjd84Mfj7hPL7E1HkL4Vo\n"
          + "Ya/yAa11eBBk84in5olxrvjrfZJZy8DFdb4pdVEE6YnzaDD1O9c1Ra6ENluNxDjv\n"
          + "PwyaehgaLCKVjFVjbDPic+ieygDmR9IADW04cuz9R+Zu2q2RELqQvtle4AMShFJy\n"
          + "GRuHWw4/LXJqAHZtc8NoGzmkxt77yuOQ5EX0gr+J0D4cFAXfVyQ2bIhnF4wiz15J\n"
          + "30lIE0PNAgMBAAECggEAKXhLDL7B8F6VmC/4uL8PhQupPVXldO5ra5W9RhwiqVGP\n"
          + "GkR11exQeI+6Hdd4+azU0F8+h0AqsOdaIOHirbmqivGipYqLr3V26d/gruMMJl4E\n"
          + "U9ZnBU9DebD+XCCn8ljWkzykyh44kCQamea14nZwQLlck9nf0/c0pFkJ80MrBJbJ\n"
          + "OfIocY5PW/UclPP0pXFYZJoO1gcI7MZwDB2kn0fCaCMsMRzpnEE9zoXXV5b+gZwL\n"
          + "T7mwwIHiJtSlD5iAcYEBy/lD9Lcbv4t1rfbr1XzQKIkg9sCrtt1dfXiHnHjCTxRt\n"
          + "Jr8BhmRxQ26O1g/5Ft7apwDprhWC/KrYpdFSjGJxKwKBgQDzyWaa9InJtknfJMqm\n"
          + "yKLztnH4wrxMvj/oqrcBnGBfiYh9dJ4fxiSwkv6PnsmH/kpjYVbUorOHxKqMxcxP\n"
          + "IOtIqh9iA2zysXir3U33cfEWW/tRpeOEaWKYwvYNd3aofst3Y7aEEZsKcXrcEr0T\n"
          + "CCLiFtg99zODbVNhxcQ4RWg3rwKBgQDVNquhoSxYaqlpJi3lB8Czam5RroHQY8I5\n"
          + "oc0eVtMMw/qv3KlEushjAL70Fly/2Vu2DpEaAE99sA4knLM3sy5SpCIQVwBrOIF3\n"
          + "kD1jlyzojU8JI6GUPpasMU28SP/fR9NEhtWke7woCiy8oU/ZqlP3pxRx7jzPzG2b\n"
          + "mJlS5vmfQwKBgQCnAmVxaG9wqZnn7cuLAM5piaaAld/r7zXXDgS7bMa1DIJd9+NP\n"
          + "vy1pbfpIp65GpRWPCaMznpbBPyDbubHiz5mAOVOwkMo1ZRFXJBACoaNY/wCoCa5Z\n"
          + "Ct1J694mkZ3PhrWa/8uMpIcDW4SgeZHgFOXY32+a29wFgILr61Emf54K7wKBgC+B\n"
          + "UNhgWssQaNKeyRcAlTTkf9P/N7lAoOPKYzNhUQDFIbPRTH2dyEwWvHUSDnRIb6Cu\n"
          + "ujG64/szINOTfnLon2eWXmiZmeRJ4L7NCoCIDF98LKHyqGupTlTrX1CWSzxqem4I\n"
          + "RM2zLAcXzUPyBSKQSskhFvMTi8UY3UsPwwmvoOqVAoGAIo6R8hkO0mGnEtAtuIp0\n"
          + "4ZrsJvUQ0F/Nse/IvdT8PBC2xcz6V3f+LaJG9yqN57fGoD6V5tme0hdPb6K//42l\n"
          + "oBJQ5zTTHMt+RtkKDyPt+K3reXIxFL4E0GiYrJpQRsjKu5xWZY73jO4zC/9aTP9y\n"
          + "0LJp16baAExMUyvsgf30c4A=\n"
          + "-----END PRIVATE KEY-----";

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
