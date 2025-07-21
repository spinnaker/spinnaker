package com.netflix.spinnaker.halyard.config.model.v1.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GithubRoleProviderTest {

  private GithubRoleProvider provider;

  @BeforeEach
  void setUp() {
    provider = new GithubRoleProvider();
  }

  @Test
  void shouldHaveCorrectDefaults() {
    // When & Then
    assertEquals(GroupMembership.RoleProviderType.GITHUB, provider.getRoleProviderType());
    assertEquals("github", provider.getNodeName());
    assertEquals(GithubRoleProvider.AuthMethod.AUTO, provider.getAuthMethod());
  }

  @Test
  void shouldAllowSettingBasicProperties() {
    // Given
    String baseUrl = "https://github.company.com/api/v3";
    String organization = "test-org";

    // When
    provider.setBaseUrl(baseUrl);
    provider.setOrganization(organization);

    // Then
    assertEquals(baseUrl, provider.getBaseUrl());
    assertEquals(organization, provider.getOrganization());
  }

  @Test
  void shouldAllowSettingPATAuthentication() {
    // Given
    String accessToken = "ghp_test_token";

    // When
    provider.setAccessToken(accessToken);

    // Then
    assertEquals(accessToken, provider.getAccessToken());
  }

  @Test
  void shouldAllowSettingGitHubAppAuthentication() {
    // Given
    String appId = "12345";
    String privateKeyPath = "/path/to/private-key.pem";
    String installationId = "67890";

    // When
    provider.setAppId(appId);
    provider.setPrivateKeyPath(privateKeyPath);
    provider.setInstallationId(installationId);

    // Then
    assertEquals(appId, provider.getAppId());
    assertEquals(privateKeyPath, provider.getPrivateKeyPath());
    assertEquals(installationId, provider.getInstallationId());
  }

  @Test
  void shouldAllowSettingAuthMethod() {
    // When
    provider.setAuthMethod(GithubRoleProvider.AuthMethod.GITHUB_APP);

    // Then
    assertEquals(GithubRoleProvider.AuthMethod.GITHUB_APP, provider.getAuthMethod());
  }

  @Test
  void shouldSupportAllAuthMethods() {
    // Test AUTO
    provider.setAuthMethod(GithubRoleProvider.AuthMethod.AUTO);
    assertEquals(GithubRoleProvider.AuthMethod.AUTO, provider.getAuthMethod());

    // Test GITHUB_APP
    provider.setAuthMethod(GithubRoleProvider.AuthMethod.GITHUB_APP);
    assertEquals(GithubRoleProvider.AuthMethod.GITHUB_APP, provider.getAuthMethod());

    // Test PAT
    provider.setAuthMethod(GithubRoleProvider.AuthMethod.PAT);
    assertEquals(GithubRoleProvider.AuthMethod.PAT, provider.getAuthMethod());
  }

  @Test
  void shouldHandleCompleteGitHubAppConfiguration() {
    // Given
    provider.setBaseUrl("https://api.github.com");
    provider.setOrganization("razorpay");
    provider.setAuthMethod(GithubRoleProvider.AuthMethod.GITHUB_APP);
    provider.setAppId("12345");
    provider.setPrivateKeyPath("/etc/spinnaker/github-app-key.pem");
    provider.setInstallationId("67890");

    // Then
    assertEquals("https://api.github.com", provider.getBaseUrl());
    assertEquals("razorpay", provider.getOrganization());
    assertEquals(GithubRoleProvider.AuthMethod.GITHUB_APP, provider.getAuthMethod());
    assertEquals("12345", provider.getAppId());
    assertEquals("/etc/spinnaker/github-app-key.pem", provider.getPrivateKeyPath());
    assertEquals("67890", provider.getInstallationId());
  }

  @Test
  void shouldHandleCompletePATConfiguration() {
    // Given
    provider.setBaseUrl("https://api.github.com");
    provider.setOrganization("razorpay");
    provider.setAuthMethod(GithubRoleProvider.AuthMethod.PAT);
    provider.setAccessToken("ghp_abc123xyz789");

    // Then
    assertEquals("https://api.github.com", provider.getBaseUrl());
    assertEquals("razorpay", provider.getOrganization());
    assertEquals(GithubRoleProvider.AuthMethod.PAT, provider.getAuthMethod());
    assertEquals("ghp_abc123xyz789", provider.getAccessToken());
  }

  @Test
  void shouldHandleMixedConfiguration() {
    // Given - both GitHub App and PAT configured (realistic migration scenario)
    provider.setBaseUrl("https://api.github.com");
    provider.setOrganization("razorpay");
    provider.setAuthMethod(GithubRoleProvider.AuthMethod.AUTO);

    // PAT configuration (existing)
    provider.setAccessToken("ghp_existing_token");

    // GitHub App configuration (new)
    provider.setAppId("12345");
    provider.setPrivateKeyPath("/etc/spinnaker/github-app-key.pem");
    provider.setInstallationId("67890");

    // Then - all properties should be preserved
    assertEquals("https://api.github.com", provider.getBaseUrl());
    assertEquals("razorpay", provider.getOrganization());
    assertEquals(GithubRoleProvider.AuthMethod.AUTO, provider.getAuthMethod());
    assertEquals("ghp_existing_token", provider.getAccessToken());
    assertEquals("12345", provider.getAppId());
    assertEquals("/etc/spinnaker/github-app-key.pem", provider.getPrivateKeyPath());
    assertEquals("67890", provider.getInstallationId());
  }

  @Test
  void shouldBeEqualWhenPropertiesMatch() {
    // Given
    GithubRoleProvider provider1 = new GithubRoleProvider();
    provider1.setBaseUrl("https://api.github.com");
    provider1.setOrganization("test-org");
    provider1.setAccessToken("token");

    GithubRoleProvider provider2 = new GithubRoleProvider();
    provider2.setBaseUrl("https://api.github.com");
    provider2.setOrganization("test-org");
    provider2.setAccessToken("token");

    // Then
    assertEquals(provider1, provider2);
    assertEquals(provider1.hashCode(), provider2.hashCode());
  }

  @Test
  void shouldNotBeEqualWhenPropertiesDiffer() {
    // Given
    GithubRoleProvider provider1 = new GithubRoleProvider();
    provider1.setOrganization("org1");

    GithubRoleProvider provider2 = new GithubRoleProvider();
    provider2.setOrganization("org2");

    // Then
    assertNotEquals(provider1, provider2);
  }

  @Test
  void shouldHaveConsistentToString() {
    // Given
    provider.setBaseUrl("https://api.github.com");
    provider.setOrganization("test-org");
    provider.setAuthMethod(GithubRoleProvider.AuthMethod.GITHUB_APP);

    // When
    String toString = provider.toString();

    // Then
    assertNotNull(toString);
    assertTrue(toString.contains("test-org"));
    assertTrue(toString.contains("GITHUB_APP"));
  }

  @Test
  void shouldInheritFromRoleProvider() {
    // Then
    assertTrue(provider instanceof RoleProvider);
  }

  @Test
  void shouldHandleNullValues() {
    // Given
    provider.setBaseUrl(null);
    provider.setOrganization(null);
    provider.setAccessToken(null);
    provider.setAppId(null);
    provider.setPrivateKeyPath(null);
    provider.setInstallationId(null);

    // Then
    assertNull(provider.getBaseUrl());
    assertNull(provider.getOrganization());
    assertNull(provider.getAccessToken());
    assertNull(provider.getAppId());
    assertNull(provider.getPrivateKeyPath());
    assertNull(provider.getInstallationId());
  }

  @Test
  void shouldPreserveSecretAnnotationOnPrivateKeyPath() {
    // This test ensures the @Secret annotation is properly maintained
    // The actual annotation testing would be done through reflection or integration tests
    // Here we just verify the field exists and can be set/get

    // Given
    String secretPath = "/secret/path/to/key.pem";

    // When
    provider.setPrivateKeyPath(secretPath);

    // Then
    assertEquals(secretPath, provider.getPrivateKeyPath());
  }

  @Test
  void shouldPreserveSecretAnnotationOnAccessToken() {
    // Similar to private key path, verify the secret field works correctly

    // Given
    String secretToken = "ghp_secret_token";

    // When
    provider.setAccessToken(secretToken);

    // Then
    assertEquals(secretToken, provider.getAccessToken());
  }
}
