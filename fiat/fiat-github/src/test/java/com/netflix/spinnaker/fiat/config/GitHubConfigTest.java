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

package com.netflix.spinnaker.fiat.config;

import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.github.GitHub;

/** Tests for GitHubConfig using hub4j/github-api library. */
class GitHubConfigTest {

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

  private GitHubConfig gitHubConfig;
  private GitHubProperties gitHubProperties;
  private Path tempPrivateKeyFile;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    gitHubConfig = new GitHubConfig();
    gitHubProperties = new GitHubProperties();
    gitHubProperties.setBaseUrl("https://api.github.com");
    gitHubProperties.setOrganization("test-org");
    gitHubConfig.setGitHubProperties(gitHubProperties);

    // Write pre-generated test private key to temp file
    tempPrivateKeyFile = tempDir.resolve("test-private-key.pem");
    Files.write(tempPrivateKeyFile, TEST_PRIVATE_KEY.getBytes());
  }

  @Test
  void shouldValidateGitHubAppConfiguration() {
    // Given
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");

    // When & Then
    assertDoesNotThrow(() -> gitHubConfig.validateConfiguration());
  }

  @Test
  void shouldValidatePATConfiguration() {
    // Given
    gitHubProperties.setAccessToken("ghp_test_token");

    // When & Then
    assertDoesNotThrow(() -> gitHubConfig.validateConfiguration());
  }

  @Test
  void shouldFailValidationWhenNoAuthConfigured() {
    // When & Then
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> gitHubConfig.validateConfiguration());

    assertTrue(exception.getMessage().contains("No GitHub authentication method configured"));
  }

  @Test
  void shouldCreateGitHubClientWithGitHubApp() {
    // Given
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");

    // When & Then - Will fail to connect (no real GitHub API) but verifies factory is called
    assertThrows(IOException.class, () -> gitHubConfig.gitHubClient());
  }

  @Test
  void shouldCreateGitHubClientWithPAT() throws IOException {
    // Given
    gitHubProperties.setAccessToken("ghp_test_token");

    // When
    GitHub client = gitHubConfig.gitHubClient();

    // Then - Client should be created (connection is lazy in hub4j)
    assertNotNull(client);
  }

  @Test
  void shouldPreferGitHubAppWhenBothConfigured() {
    // Given
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");
    gitHubProperties.setAccessToken("ghp_test_token");

    // When & Then
    assertTrue(gitHubProperties.shouldUseGitHubApp());
  }

  @Test
  void shouldUseCorrectBaseUrl() throws IOException {
    // Given
    gitHubProperties.setAccessToken("ghp_test_token");
    gitHubProperties.setBaseUrl("https://github.company.com/api/v3");

    // When
    GitHub client = gitHubConfig.gitHubClient();

    // Then - GitHub Enterprise URL should work
    assertNotNull(client);
  }

  @Test
  void shouldForceGitHubAppMode() {
    // Given
    gitHubProperties.setAuthMethod(GitHubProperties.AuthMethod.GITHUB_APP);
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");

    // When & Then
    assertTrue(gitHubProperties.shouldUseGitHubApp());
    assertDoesNotThrow(() -> gitHubConfig.validateConfiguration());
  }

  @Test
  void shouldForcePATMode() {
    // Given
    gitHubProperties.setAuthMethod(GitHubProperties.AuthMethod.PAT);
    gitHubProperties.setAccessToken("ghp_test_token");
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");

    // When & Then
    assertFalse(gitHubProperties.shouldUseGitHubApp());
    assertDoesNotThrow(() -> gitHubConfig.validateConfiguration());
  }

  @Test
  void shouldFailWhenForceGitHubAppButNotConfigured() {
    // Given
    gitHubProperties.setAuthMethod(GitHubProperties.AuthMethod.GITHUB_APP);
    gitHubProperties.setAccessToken("ghp_test_token"); // Only PAT configured

    // When & Then
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> gitHubConfig.validateConfiguration());

    assertTrue(
        exception
            .getMessage()
            .contains("GitHub App authentication is forced but not properly configured"));
  }

  @Test
  void shouldFailWhenForcePATButNotConfigured() {
    // Given
    gitHubProperties.setAuthMethod(GitHubProperties.AuthMethod.PAT);
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath(tempPrivateKeyFile.toString());
    gitHubProperties.setInstallationId("67890");

    // When & Then
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> gitHubConfig.validateConfiguration());

    assertTrue(
        exception
            .getMessage()
            .contains("PAT authentication is forced but 'accessToken' is not configured"));
  }

  @Test
  void shouldHandleInvalidPrivateKeyPath() {
    // Given
    gitHubProperties.setAppId("12345");
    gitHubProperties.setPrivateKeyPath("/nonexistent/path/private-key.pem");
    gitHubProperties.setInstallationId("67890");

    // When & Then
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> gitHubConfig.gitHubClient());

    assertTrue(exception.getMessage().contains("Failed to load GitHub App private key"));
  }
}
