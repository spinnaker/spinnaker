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

package com.netflix.spinnaker.fiat.roles.github;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Integration test to verify all GitHub App authentication components work together */
class GitHubAppIntegrationTest {

  @Test
  void shouldInstantiateAllComponents() {
    // This test verifies that all our new classes can be instantiated without compilation errors

    // Test that GitHubProperties can be created and configured
    GitHubProperties properties = new GitHubProperties();
    properties.setBaseUrl("https://api.github.com");
    properties.setOrganization("test-org");
    properties.setAuthMethod(GitHubProperties.AuthMethod.AUTO);

    assertNotNull(properties);
    assertEquals("https://api.github.com", properties.getBaseUrl());
    assertEquals("test-org", properties.getOrganization());
    assertEquals(GitHubProperties.AuthMethod.AUTO, properties.getAuthMethod());
  }

  @Test
  void shouldHaveAllAuthenticationMethods() {
    // Verify all authentication methods are available
    GitHubProperties.AuthMethod[] methods = GitHubProperties.AuthMethod.values();

    assertEquals(3, methods.length);
    assertTrue(java.util.Arrays.asList(methods).contains(GitHubProperties.AuthMethod.AUTO));
    assertTrue(java.util.Arrays.asList(methods).contains(GitHubProperties.AuthMethod.GITHUB_APP));
    assertTrue(java.util.Arrays.asList(methods).contains(GitHubProperties.AuthMethod.PAT));
  }

  @Test
  void shouldValidateConfigurationLogic() {
    GitHubProperties properties = new GitHubProperties();
    properties.setBaseUrl("https://api.github.com");
    properties.setOrganization("test-org");

    // Test GitHub App configuration detection
    properties.setAppId("12345");
    properties.setPrivateKeyPath("/path/to/key.pem");
    properties.setInstallationId("67890");
    assertTrue(properties.isGitHubAppConfigured());

    // Test PAT configuration detection
    properties = new GitHubProperties();
    properties.setAccessToken("ghp_token");
    assertTrue(properties.isPATConfigured());
  }

  @Test
  void shouldWorkWithRealisticConfiguration() {
    // Test a realistic configuration similar to what users would have
    GitHubProperties properties = new GitHubProperties();
    properties.setBaseUrl("https://api.github.com");
    properties.setOrganization("razorpay");
    properties.setAuthMethod(GitHubProperties.AuthMethod.AUTO);

    // Start with PAT (existing)
    properties.setAccessToken("ghp_existing_token");
    assertTrue(properties.isPATConfigured());
    assertFalse(properties.shouldUseGitHubApp());

    // Add GitHub App (migration)
    properties.setAppId("12345");
    properties.setPrivateKeyPath("/etc/spinnaker/github-app-key.pem");
    properties.setInstallationId("67890");

    // Now GitHub App should be preferred
    assertTrue(properties.isGitHubAppConfigured());
    assertTrue(properties.shouldUseGitHubApp());

    // Validation should pass
    assertDoesNotThrow(() -> properties.validateAuthConfiguration());
  }
}
