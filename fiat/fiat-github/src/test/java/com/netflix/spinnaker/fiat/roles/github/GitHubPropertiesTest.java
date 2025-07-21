package com.netflix.spinnaker.fiat.roles.github;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitHubPropertiesTest {

  private GitHubProperties properties;

  @BeforeEach
  void setUp() {
    properties = new GitHubProperties();
    properties.setBaseUrl("https://api.github.com");
    properties.setOrganization("test-org");
  }

  @Test
  void shouldDetectGitHubAppConfiguration() {
    // Given
    properties.setAppId("12345");
    properties.setPrivateKeyPath("/path/to/private-key.pem");
    properties.setInstallationId("67890");

    // When & Then
    assertTrue(properties.isGitHubAppConfigured());
  }

  @Test
  void shouldDetectIncompleteGitHubAppConfiguration() {
    // Given - missing installation ID
    properties.setAppId("12345");
    properties.setPrivateKeyPath("/path/to/private-key.pem");

    // When & Then
    assertFalse(properties.isGitHubAppConfigured());
  }

  @Test
  void shouldDetectPATConfiguration() {
    // Given
    properties.setAccessToken("ghp_test_token");

    // When & Then
    assertTrue(properties.isPATConfigured());
  }

  @Test
  void shouldDetectMissingPATConfiguration() {
    // When & Then
    assertFalse(properties.isPATConfigured());
  }

  @Test
  void shouldPreferGitHubAppInAutoMode() {
    // Given - both configured
    properties.setAuthMethod(GitHubProperties.AuthMethod.AUTO);
    properties.setAppId("12345");
    properties.setPrivateKeyPath("/path/to/private-key.pem");
    properties.setInstallationId("67890");
    properties.setAccessToken("ghp_test_token");

    // When & Then
    assertTrue(properties.shouldUseGitHubApp());
  }

  @Test
  void shouldFallbackToPATInAutoMode() {
    // Given - only PAT configured
    properties.setAuthMethod(GitHubProperties.AuthMethod.AUTO);
    properties.setAccessToken("ghp_test_token");

    // When & Then
    assertFalse(properties.shouldUseGitHubApp());
  }

  @Test
  void shouldForceGitHubAppMode() {
    // Given
    properties.setAuthMethod(GitHubProperties.AuthMethod.GITHUB_APP);
    properties.setAppId("12345");
    properties.setPrivateKeyPath("/path/to/private-key.pem");
    properties.setInstallationId("67890");

    // When & Then
    assertTrue(properties.shouldUseGitHubApp());
  }

  @Test
  void shouldFailWhenForceGitHubAppModeButNotConfigured() {
    // Given
    properties.setAuthMethod(GitHubProperties.AuthMethod.GITHUB_APP);
    properties.setAccessToken("ghp_test_token"); // Only PAT configured

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              properties.shouldUseGitHubApp();
            });

    assertTrue(
        exception
            .getMessage()
            .contains("GitHub App authentication is forced but not properly configured"));
  }

  @Test
  void shouldForcePATMode() {
    // Given
    properties.setAuthMethod(GitHubProperties.AuthMethod.PAT);
    properties.setAccessToken("ghp_test_token");
    properties.setAppId("12345"); // GitHub App also configured
    properties.setPrivateKeyPath("/path/to/private-key.pem");
    properties.setInstallationId("67890");

    // When & Then
    assertFalse(properties.shouldUseGitHubApp());
  }

  @Test
  void shouldFailWhenForcePATModeButNotConfigured() {
    // Given
    properties.setAuthMethod(GitHubProperties.AuthMethod.PAT);
    properties.setAppId("12345"); // Only GitHub App configured
    properties.setPrivateKeyPath("/path/to/private-key.pem");
    properties.setInstallationId("67890");

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              properties.shouldUseGitHubApp();
            });

    assertTrue(
        exception
            .getMessage()
            .contains("PAT authentication is forced but 'accessToken' is not configured"));
  }

  @Test
  void shouldFailWhenNoAuthenticationConfigured() {
    // Given - no authentication method configured
    properties.setAuthMethod(GitHubProperties.AuthMethod.AUTO);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              properties.shouldUseGitHubApp();
            });

    assertTrue(exception.getMessage().contains("No GitHub authentication method configured"));
  }

  @Test
  void shouldValidateConfigurationSuccessfully() {
    // Given
    properties.setAccessToken("ghp_test_token");

    // When & Then
    assertDoesNotThrow(() -> properties.validateAuthConfiguration());
  }

  @Test
  void shouldFailValidationWhenNoAuthConfigured() {
    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              properties.validateAuthConfiguration();
            });

    assertTrue(exception.getMessage().contains("No GitHub authentication method configured"));
  }

  @Test
  void shouldDefaultToAutoMode() {
    // When & Then
    assertEquals(GitHubProperties.AuthMethod.AUTO, properties.getAuthMethod());
  }

  @Test
  void shouldHandleEmptyStringsAsNotConfigured() {
    // Given
    properties.setAppId("");
    properties.setPrivateKeyPath("   ");
    properties.setInstallationId(null);
    properties.setAccessToken("");

    // When & Then
    assertFalse(properties.isGitHubAppConfigured());
    assertFalse(properties.isPATConfigured());
  }

  @Test
  void shouldHandleWhitespaceOnlyStringsAsNotConfigured() {
    // Given
    properties.setAppId("   ");
    properties.setPrivateKeyPath("\t\n");
    properties.setInstallationId("  ");
    properties.setAccessToken("   ");

    // When & Then
    assertFalse(properties.isGitHubAppConfigured());
    assertFalse(properties.isPATConfigured());
  }

  @Test
  void shouldDetectPartialGitHubAppConfiguration() {
    // Test missing appId
    properties.setPrivateKeyPath("/path/to/key.pem");
    properties.setInstallationId("67890");
    assertFalse(properties.isGitHubAppConfigured());

    // Test missing privateKeyPath
    properties = new GitHubProperties();
    properties.setAppId("12345");
    properties.setInstallationId("67890");
    assertFalse(properties.isGitHubAppConfigured());

    // Test missing installationId
    properties = new GitHubProperties();
    properties.setAppId("12345");
    properties.setPrivateKeyPath("/path/to/key.pem");
    assertFalse(properties.isGitHubAppConfigured());
  }

  @Test
  void shouldWorkWithValidCompleteGitHubAppConfiguration() {
    // Given
    properties.setAppId("12345");
    properties.setPrivateKeyPath("/path/to/private-key.pem");
    properties.setInstallationId("67890");

    // When & Then
    assertTrue(properties.isGitHubAppConfigured());
    assertTrue(properties.shouldUseGitHubApp());
    assertDoesNotThrow(() -> properties.validateAuthConfiguration());
  }
}
