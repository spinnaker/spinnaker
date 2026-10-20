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

import com.google.common.base.Ticker;
import com.google.common.util.concurrent.MoreExecutors;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

/**
 * Unit tests for GithubTeamsUserRolesProvider using hub4j/github-api library.
 *
 * <p><b>Testing Strategy:</b> These tests mock the {@link GitHub} client directly rather than using
 * a mock HTTP server. This approach tests the provider's business logic (role assignment, caching,
 * error handling) without testing the hub4j library's HTTP implementation.
 *
 * <p><b>Rationale:</b>
 *
 * <ul>
 *   <li>hub4j/github-api is a mature, well-tested library with its own test suite
 *   <li>Unit tests should be fast and deterministic - mocking at the client level achieves this
 *   <li>This pattern is consistent with other tests in fiat (see ClouddriverApplicationLoaderTest)
 * </ul>
 *
 * <p><b>Coverage:</b> These tests verify:
 *
 * <ul>
 *   <li>Role assignment logic (org membership, team membership)
 *   <li>Case-insensitive username handling
 *   <li>Error handling (404, 401, 403, rate limits)
 *   <li>Cache behavior and invalidation
 *   <li>Multi-user batch loading
 * </ul>
 *
 * @see GithubTeamsUserRolesProviderIntegrationTest for HTTP-level integration tests using WireMock
 */
@ExtendWith(MockitoExtension.class)
class GithubTeamsUserRolesProviderTest {

  @Mock private GitHub gitHubClient;

  @Mock private GHOrganization organization;

  @Mock private GHTeam team1;

  @Mock private GHTeam team2;

  @Mock private GHUser user1;

  @Mock private GHUser user2;

  @Mock private PagedIterable<GHUser> orgMembers;

  @Mock private PagedIterable<GHTeam> orgTeams;

  @Mock private PagedIterable<GHUser> team1Members;

  @Mock private PagedIterable<GHUser> team2Members;

  private GithubTeamsUserRolesProvider provider;
  private GitHubProperties gitHubProperties;
  private final FakeTicker ticker = new FakeTicker();

  @BeforeEach
  void setUp() throws IOException {
    provider = new GithubTeamsUserRolesProvider();
    gitHubProperties = new GitHubProperties();
    gitHubProperties.setBaseUrl("https://api.github.com");
    gitHubProperties.setOrganization("test-org");
    gitHubProperties.setAccessToken("ghp_test_token");

    provider.setGitHubClient(gitHubClient);
    provider.setGitHubProperties(gitHubProperties);
    // Cache time moves only when a test advances it, and refreshes run inline on the reading thread
    provider.setTicker(ticker);
    provider.setExecutor(MoreExecutors.newDirectExecutorService());

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

    lenient().when(organization.listTeams()).thenReturn(orgTeams);
    lenient().when(orgTeams.toList()).thenReturn(Arrays.asList(team1, team2));
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
    when(orgTeams.toList()).thenReturn(Collections.emptyList());

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
        exception.getMessage().contains("GitHub API error"),
        "Exception message should contain 'GitHub API error', but was: " + exception.getMessage());
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
        exception.getMessage().contains("GitHub API error"),
        "Exception message should contain 'GitHub API error', but was: " + exception.getMessage());
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
        exception.getMessage().contains("GitHub API error"),
        "Exception message should contain 'GitHub API error', but was: " + exception.getMessage());
  }

  @Test
  void shouldThrowExceptionForOtherIOExceptions() throws Exception {
    // Given
    provider.afterPropertiesSet();
    provider.invalidateAll(); // Clear cache to force fresh fetch

    // Mock generic IOException (network error, etc.)
    when(gitHubClient.getOrganization("test-org")).thenThrow(new IOException("Connection timeout"));

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When & Then - Should throw rather than report the user as having no roles
    ProviderException exception =
        assertThrows(ProviderException.class, () -> provider.loadRoles(externalUser));

    assertTrue(
        exception.getMessage().contains("GitHub API error"),
        "Exception message should contain 'GitHub API error', but was: " + exception.getMessage());
  }

  @Test
  void shouldKeepCachedMembersWhenRefreshFails() throws Exception {
    AtomicInteger fetches = new AtomicInteger();
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenAnswer(succeedOnceThenFail(Arrays.asList(user1), fetches));
    stubTeamMembers();

    assertCachedRolesSurviveFailedRefreshes(fetches);
  }

  @Test
  void shouldKeepCachedTeamsWhenRefreshFails() throws Exception {
    AtomicInteger fetches = new AtomicInteger();
    stubOrgMembers();
    when(orgTeams.toList()).thenAnswer(succeedOnceThenFail(Arrays.asList(team1, team2), fetches));
    stubTeamMembers();

    assertCachedRolesSurviveFailedRefreshes(fetches);
  }

  @Test
  void shouldKeepCachedValuesWhenRefreshFailsWithUncheckedException() throws Exception {
    // e.g. hub4j's GHException, which its paged iterators throw when a page fails to load
    AtomicInteger fetches = new AtomicInteger();
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList())
        .thenAnswer(
            succeedOnceThenFail(
                Arrays.asList(user1),
                fetches,
                new GHException(
                    "Failed to retrieve members", new IOException("Connection reset"))));
    stubTeamMembers();

    assertCachedRolesSurviveFailedRefreshes(fetches);
  }

  @Test
  void shouldKeepCachedTeamMembershipsWhenRefreshFails() throws Exception {
    AtomicInteger fetches = new AtomicInteger();
    stubOrgMembers();
    when(organization.getTeamBySlug("team-1")).thenReturn(team1);
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenAnswer(succeedOnceThenFail(Arrays.asList(user1), fetches));
    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Collections.emptyList());

    assertCachedRolesSurviveFailedRefreshes(fetches);
  }

  /**
   * Loads user1's roles (org + team-1), then lets the refresh interval elapse twice while the
   * stubbed GitHub call fails. The cached roles must keep being served, and a failed refresh must
   * not be retried until the next interval.
   *
   * @param fetches the number of calls made to the stubbed GitHub call that fails after its first
   *     call
   */
  private void assertCachedRolesSurviveFailedRefreshes(AtomicInteger fetches) throws Exception {
    provider.afterPropertiesSet();
    ExternalUser externalUser = new ExternalUser().setId("user1");
    Duration refreshInterval =
        Duration.ofSeconds(gitHubProperties.getMembershipCacheTTLSeconds() + 1);

    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));
    assertEquals(1, fetches.get());

    // The interval elapses, so the next read refreshes; the refresh fails and keeps the old value
    ticker.advance(refreshInterval);
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));
    assertEquals(2, fetches.get());

    // The failed refresh counts as a fresh write, so reads within the interval make no new calls
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));
    assertEquals(2, fetches.get());

    // After another interval the refresh is retried, and the old value is still served
    ticker.advance(refreshInterval);
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));
    assertEquals(3, fetches.get());
  }

  private static void assertOrgAndTeam1Roles(List<Role> roles) {
    assertEquals(
        Set.of("test-org", "team-1"),
        roles.stream().map(Role::getName).collect(Collectors.toSet()));
  }

  /** Returns {@code value} on the first call and fails like a network error on every later one. */
  private static <T> Answer<T> succeedOnceThenFail(T value, AtomicInteger calls) {
    return succeedOnceThenFail(value, calls, new IOException("Connection reset"));
  }

  /** Returns {@code value} on the first call and throws {@code failure} on every later one. */
  private static <T> Answer<T> succeedOnceThenFail(
      T value, AtomicInteger calls, Throwable failure) {
    return invocation -> {
      if (calls.incrementAndGet() == 1) {
        return value;
      }
      throw failure;
    };
  }

  /** Stubs the org members as user1 only. */
  private void stubOrgMembers() throws IOException {
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1));
  }

  /** Stubs team-1 with user1 as its only member, and team-2 with no members. */
  private void stubTeamMembers() throws IOException {
    when(organization.getTeamBySlug("team-1")).thenReturn(team1);
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Arrays.asList(user1));
    when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Collections.emptyList());
  }

  /** A cache clock that moves only when a test advances it. */
  private static class FakeTicker extends Ticker {
    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read() {
      return nanos.get();
    }

    void advance(Duration duration) {
      nanos.addAndGet(duration.toNanos());
    }
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

    // team-2 is only fetched if it is checked before team-1 fails
    lenient().when(organization.getTeamBySlug("team-2")).thenReturn(team2);
    lenient().when(team2.listMembers()).thenReturn(team2Members);
    lenient().when(team2Members.toList()).thenReturn(Collections.emptyList());

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When & Then - Should throw RuntimeException to preserve cached values
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> provider.loadRoles(externalUser));

    assertTrue(
        exception.getMessage().contains("GitHub API error"),
        "Exception message should contain 'GitHub API error', but was: " + exception.getMessage());
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

      assertTrue(runtimeException.getMessage().contains("GitHub API error"));
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

      assertTrue(runtimeException.getMessage().contains("GitHub API error"));
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

      assertTrue(runtimeException.getMessage().contains("GitHub API error"));
    }
  }
}
