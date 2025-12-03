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
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests for GithubTeamsUserRolesProvider using hub4j/github-api library. */
@ExtendWith(MockitoExtension.class)
class GithubTeamsUserRolesProviderTest {

  @Mock private GitHub gitHubClient;

  @Mock private GHOrganization organization;

  @Mock private GHTeam team1;

  @Mock private GHTeam team2;

  @Mock private GHUser user1;

  @Mock private GHUser user2;

  @Mock private PagedIterable<GHUser> orgMembers;

  @Mock private PagedIterable<GHUser> team1Members;

  @Mock private PagedIterable<GHUser> team2Members;

  private GithubTeamsUserRolesProvider provider;
  private GitHubProperties gitHubProperties;

  @BeforeEach
  void setUp() throws IOException {
    provider = new GithubTeamsUserRolesProvider();
    gitHubProperties = new GitHubProperties();
    gitHubProperties.setBaseUrl("https://api.github.com");
    gitHubProperties.setOrganization("test-org");
    gitHubProperties.setAccessToken("ghp_test_token");

    provider.setGitHubClient(gitHubClient);
    provider.setGitHubProperties(gitHubProperties);

    // Use lenient() for stubbings that aren't used in all tests
    lenient().when(gitHubClient.getOrganization("test-org")).thenReturn(organization);

    // Mock users
    lenient().when(user1.getLogin()).thenReturn("user1");
    lenient().when(user2.getLogin()).thenReturn("user2");

    // Mock teams
    lenient().when(team1.getName()).thenReturn("Team 1");
    lenient().when(team1.getSlug()).thenReturn("team-1");
    lenient().when(team2.getName()).thenReturn("Team 2");
    lenient().when(team2.getSlug()).thenReturn("team-2");

    Map<String, GHTeam> teams = new HashMap<>();
    teams.put("team-1", team1);
    teams.put("team-2", team2);
    lenient().when(organization.getTeams()).thenReturn(teams);
  }

  @Test
  void shouldInitializeWithValidConfiguration() throws Exception {
    // When & Then
    assertDoesNotThrow(() -> provider.afterPropertiesSet());
  }

  @Test
  void shouldFailInitializationWithoutOrganization() {
    // Given
    gitHubProperties.setOrganization(null);

    // When & Then
    assertThrows(IllegalStateException.class, () -> provider.afterPropertiesSet());
  }

  @Test
  void shouldFailInitializationWithoutBaseUrl() {
    // Given
    gitHubProperties.setBaseUrl(null);

    // When & Then
    assertThrows(IllegalStateException.class, () -> provider.afterPropertiesSet());
  }

  @Test
  void shouldLoadRolesForOrgMember() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Mock org members
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1, user2));

    // Mock team 1 members
    when(organization.getTeamBySlug("team-1")).thenReturn(team1);
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Arrays.asList(user1));

    // Mock team 2 members
    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Collections.emptyList());

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    assertNotNull(roles);
    assertEquals(2, roles.size()); // org + team1

    assertTrue(roles.stream().anyMatch(r -> r.getName().equals("test-org")));
    assertTrue(roles.stream().anyMatch(r -> r.getName().equals("team-1")));
    assertFalse(roles.stream().anyMatch(r -> r.getName().equals("team-2")));

    roles.forEach(role -> assertEquals(Role.Source.GITHUB_TEAMS, role.getSource()));
  }

  @Test
  void shouldReturnEmptyRolesForNonOrgMember() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Mock org members (doesn't include user3)
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1, user2));

    ExternalUser externalUser = new ExternalUser().setId("user3");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    assertNotNull(roles);
    assertTrue(roles.isEmpty());
  }

  @Test
  void shouldReturnEmptyRolesForEmptyUsername() throws Exception {
    // Given
    provider.afterPropertiesSet();
    ExternalUser externalUser = new ExternalUser().setId("");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    assertNotNull(roles);
    assertTrue(roles.isEmpty());
  }

  @Test
  void shouldReturnEmptyRolesForNullUsername() throws Exception {
    // Given
    provider.afterPropertiesSet();
    ExternalUser externalUser = new ExternalUser().setId(null);

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    assertNotNull(roles);
    assertTrue(roles.isEmpty());
  }

  @Test
  void shouldHandleCaseInsensitiveUsernames() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Mock org members with uppercase login
    GHUser upperCaseUser = mock(GHUser.class);
    when(upperCaseUser.getLogin()).thenReturn("USER1");

    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(upperCaseUser));

    when(organization.getTeamBySlug("team-1")).thenReturn(team1);
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Collections.emptyList());

    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Collections.emptyList());

    // Test with lowercase username
    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    assertNotNull(roles);
    assertEquals(1, roles.size()); // Just org role
    assertTrue(roles.stream().anyMatch(r -> r.getName().equals("test-org")));
  }

  @Test
  void shouldLoadRolesForMultipleUsers() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Mock org members
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1, user2));

    when(organization.getTeamBySlug("team-1")).thenReturn(team1);
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Arrays.asList(user1));

    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Arrays.asList(user2));

    Collection<ExternalUser> users =
        Arrays.asList(new ExternalUser().setId("user1"), new ExternalUser().setId("user2"));

    // When
    Map<String, Collection<Role>> rolesMap = provider.multiLoadRoles(users);

    // Then
    assertNotNull(rolesMap);
    assertEquals(2, rolesMap.size());

    Collection<Role> user1Roles = rolesMap.get("user1");
    assertNotNull(user1Roles);
    assertEquals(2, user1Roles.size()); // org + team1

    Collection<Role> user2Roles = rolesMap.get("user2");
    assertNotNull(user2Roles);
    assertEquals(2, user2Roles.size()); // org + team2
  }

  @Test
  void shouldHandleEmptyUserCollection() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // When
    Map<String, Collection<Role>> rolesMap = provider.multiLoadRoles(Collections.emptyList());

    // Then
    assertNotNull(rolesMap);
    assertTrue(rolesMap.isEmpty());
  }

  @Test
  void shouldHandleNullUserCollection() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // When
    Map<String, Collection<Role>> rolesMap = provider.multiLoadRoles(null);

    // Then
    assertNotNull(rolesMap);
    assertTrue(rolesMap.isEmpty());
  }

  @Test
  void shouldHandleNoTeams() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Mock org members
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1));

    // Mock no teams
    when(organization.getTeams()).thenReturn(Collections.emptyMap());

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    assertNotNull(roles);
    assertEquals(1, roles.size()); // Just org role
    assertTrue(roles.stream().anyMatch(r -> r.getName().equals("test-org")));
  }

  @Test
  void shouldConvertRoleNamesToLowerCase() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Mock org members
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1));

    when(organization.getTeamBySlug("team-1")).thenReturn(team1);
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Arrays.asList(user1));

    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Collections.emptyList());

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    roles.forEach(role -> assertEquals(role.getName(), role.getName().toLowerCase()));
  }

  @Test
  void shouldSetCorrectRoleSource() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Mock org members
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1));

    when(organization.getTeamBySlug("team-1")).thenReturn(team1);
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Collections.emptyList());

    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Collections.emptyList());

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    roles.forEach(role -> assertEquals(Role.Source.GITHUB_TEAMS, role.getSource()));
  }

  // ===== Error Handling Tests =====
  // These tests verify the critical error handling behavior that prevents users from losing access
  // during transient GitHub API errors.

  @Test
  void shouldReturnEmptySetFor404OrganizationNotFound() throws Exception {
    // Given
    provider.afterPropertiesSet();
    provider.invalidateAll(); // Clear cache to force fresh fetch

    // Mock 404 error (organization not found)
    when(gitHubClient.getOrganization("test-org"))
        .thenThrow(new GHFileNotFoundException("Organization not found"));

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then - Should return empty roles (org doesn't exist, no point retrying)
    assertNotNull(roles);
    assertTrue(roles.isEmpty());
  }

  @Test
  void shouldThrowExceptionFor401Unauthorized() throws Exception {
    // Given
    provider.afterPropertiesSet();
    provider.invalidateAll(); // Clear cache to force fresh fetch

    // Mock 401 error (authentication failed)
    when(gitHubClient.getOrganization("test-org")).thenThrow(new IOException("401 Unauthorized"));

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When & Then - Should throw RuntimeException to preserve cached values
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> provider.loadRoles(externalUser));

    assertTrue(
        exception.getMessage().contains("Critical GitHub API error"),
        "Exception message should contain 'Critical GitHub API error', but was: "
            + exception.getMessage());
  }

  @Test
  void shouldThrowExceptionFor403Forbidden() throws Exception {
    // Given
    provider.afterPropertiesSet();
    provider.invalidateAll(); // Clear cache to force fresh fetch

    // Mock 403 error (access forbidden)
    when(gitHubClient.getOrganization("test-org")).thenThrow(new IOException("403 Forbidden"));

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When & Then - Should throw RuntimeException to preserve cached values
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> provider.loadRoles(externalUser));

    assertTrue(
        exception.getMessage().contains("Critical GitHub API error"),
        "Exception message should contain 'Critical GitHub API error', but was: "
            + exception.getMessage());
  }

  @Test
  void shouldThrowExceptionForRateLimitExceeded() throws Exception {
    // Given
    provider.afterPropertiesSet();
    provider.invalidateAll(); // Clear cache to force fresh fetch

    // Mock rate limit error
    when(gitHubClient.getOrganization("test-org"))
        .thenThrow(new IOException("API rate limit exceeded"));

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When & Then - Should throw RuntimeException to preserve cached values
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> provider.loadRoles(externalUser));

    assertTrue(
        exception.getMessage().contains("Critical GitHub API error"),
        "Exception message should contain 'Critical GitHub API error', but was: "
            + exception.getMessage());
  }

  @Test
  void shouldReturnEmptySetForOtherIOExceptions() throws Exception {
    // Given
    provider.afterPropertiesSet();
    provider.invalidateAll(); // Clear cache to force fresh fetch

    // Mock generic IOException (network error, etc.)
    when(gitHubClient.getOrganization("test-org")).thenThrow(new IOException("Connection timeout"));

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then - Should return empty roles for non-critical errors
    assertNotNull(roles);
    assertTrue(roles.isEmpty());
  }

  @Test
  void shouldReturnEmptySetFor404TeamNotFound() throws Exception {
    // Given
    provider.afterPropertiesSet();
    provider.invalidateAll(); // Clear cache to force fresh fetch

    // Mock successful org members fetch
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1));

    // Mock successful teams fetch, but team lookup throws 404
    when(organization.getTeamBySlug("team-1"))
        .thenThrow(new GHFileNotFoundException("Team not found"));
    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Collections.emptyList());

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then - Should return org role only (team not found is acceptable)
    assertNotNull(roles);
    assertEquals(1, roles.size());
    assertTrue(roles.stream().anyMatch(r -> r.getName().equals("test-org")));
  }

  @Test
  void shouldThrowExceptionFor401WhenFetchingTeamMembers() throws Exception {
    // Given
    provider.afterPropertiesSet();
    provider.invalidateAll(); // Clear cache to force fresh fetch

    // Mock successful org members fetch
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1));

    // Mock 401 error when fetching team members
    when(organization.getTeamBySlug("team-1")).thenReturn(team1);
    when(team1.listMembers()).thenThrow(new IOException("401 Unauthorized"));

    // Need to setup team-2 as well
    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Collections.emptyList());

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When & Then - Should throw RuntimeException to preserve cached values
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> provider.loadRoles(externalUser));

    assertTrue(
        exception.getMessage().contains("Critical GitHub API error"),
        "Exception message should contain 'Critical GitHub API error', but was: "
            + exception.getMessage());
  }

  @Test
  void shouldPreserveCachedValuesOnTransientErrors() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // First call succeeds - populate cache
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1));
    when(organization.getTeamBySlug("team-1")).thenReturn(team1);
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Arrays.asList(user1));
    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Collections.emptyList());

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // First call - cache population
    List<Role> rolesBeforeError = provider.loadRoles(externalUser);
    assertNotNull(rolesBeforeError);
    assertEquals(2, rolesBeforeError.size()); // org + team1

    // Verify that RuntimeException is thrown for 401 errors on fresh fetch
    // This ensures cache can preserve stale values when underlying fetch fails
    reset(gitHubClient);
    lenient().when(gitHubClient.getOrganization("test-org")).thenReturn(organization);
    provider.invalidateAll(); // Clear cache to force fresh fetch
    when(gitHubClient.getOrganization("test-org")).thenThrow(new IOException("401 Unauthorized"));

    // When - Attempt to fetch with error (bypassing cache)
    // In production, Guava cache would catch this exception and serve stale data
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> provider.loadRoles(externalUser));

    // Then - Exception is thrown, allowing cache layer to preserve old values
    assertTrue(
        exception.getMessage().contains("Critical GitHub API error"),
        "Exception message should contain 'Critical GitHub API error', but was: "
            + exception.getMessage());
  }

  @Test
  void shouldHandleMixedErrorScenarios() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Mock successful org members fetch
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1));

    // Mock one team with 404 (not found), one with success
    when(organization.getTeamBySlug("team-1"))
        .thenThrow(new GHFileNotFoundException("Team not found"));
    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Arrays.asList(user1));

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then - Should return org + team2 (team1 404 is ignored)
    assertNotNull(roles);
    assertEquals(2, roles.size());
    assertTrue(roles.stream().anyMatch(r -> r.getName().equals("test-org")));
    assertTrue(roles.stream().anyMatch(r -> r.getName().equals("team-2")));
  }

  @Test
  void shouldHandle401ErrorMessageVariations() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Test various 401 error message formats
    String[] errorMessages = {
      "401 Unauthorized", "HTTP 401: Unauthorized", "Error 401 - Authentication failed", "401"
    };

    for (String errorMessage : errorMessages) {
      // Reset the mock and invalidate cache
      reset(gitHubClient);
      lenient().when(gitHubClient.getOrganization("test-org")).thenReturn(organization);
      provider.invalidateAll(); // Clear cache to force fresh fetch

      // Mock error
      IOException exception = new IOException(errorMessage);
      when(gitHubClient.getOrganization("test-org")).thenThrow(exception);

      ExternalUser externalUser = new ExternalUser().setId("user1");

      // When & Then - Should throw RuntimeException for all 401 variations
      RuntimeException runtimeException =
          assertThrows(
              RuntimeException.class,
              () -> provider.loadRoles(externalUser),
              "Failed for error message: " + errorMessage);

      assertTrue(runtimeException.getMessage().contains("Critical GitHub API error"));
    }
  }

  @Test
  void shouldHandle403ErrorMessageVariations() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Test various 403 error message formats
    String[] errorMessages = {
      "403 Forbidden", "HTTP 403: Access denied", "Error 403 - Forbidden", "403"
    };

    for (String errorMessage : errorMessages) {
      // Reset the mock and invalidate cache
      reset(gitHubClient);
      lenient().when(gitHubClient.getOrganization("test-org")).thenReturn(organization);
      provider.invalidateAll(); // Clear cache to force fresh fetch

      // Mock error
      IOException exception = new IOException(errorMessage);
      when(gitHubClient.getOrganization("test-org")).thenThrow(exception);

      ExternalUser externalUser = new ExternalUser().setId("user1");

      // When & Then - Should throw RuntimeException for all 403 variations
      RuntimeException runtimeException =
          assertThrows(
              RuntimeException.class,
              () -> provider.loadRoles(externalUser),
              "Failed for error message: " + errorMessage);

      assertTrue(runtimeException.getMessage().contains("Critical GitHub API error"));
    }
  }

  @Test
  void shouldHandleRateLimitErrorMessageVariations() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Test various rate limit error message formats
    String[] errorMessages = {
      "API rate limit exceeded",
      "api rate limit exceeded for user",
      "API RATE LIMIT EXCEEDED",
      "Rate limit exceeded"
    };

    for (String errorMessage : errorMessages) {
      // Reset the mock and invalidate cache
      reset(gitHubClient);
      lenient().when(gitHubClient.getOrganization("test-org")).thenReturn(organization);
      provider.invalidateAll(); // Clear cache to force fresh fetch

      // Mock error
      IOException exception = new IOException(errorMessage);
      when(gitHubClient.getOrganization("test-org")).thenThrow(exception);

      ExternalUser externalUser = new ExternalUser().setId("user1");

      // When & Then - Should throw RuntimeException for all rate limit variations
      RuntimeException runtimeException =
          assertThrows(
              RuntimeException.class,
              () -> provider.loadRoles(externalUser),
              "Failed for error message: " + errorMessage);

      assertTrue(runtimeException.getMessage().contains("Critical GitHub API error"));
    }
  }
}
