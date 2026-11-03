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
import com.netflix.spinnaker.kork.github.GitHubAppAuthenticator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  @BeforeEach
  void setUp() throws IOException {
    provider = new GithubTeamsUserRolesProvider();
    gitHubProperties = new GitHubProperties();
    gitHubProperties.setBaseUrl("https://api.github.com");
    gitHubProperties.setOrganization("test-org");
    gitHubProperties.setAccessToken("ghp_test_token");
    // Tests warm the members cache explicitly rather than on a background schedule
    gitHubProperties.setHealthWarmIntervalSeconds(0);

    provider.setGitHubClient(gitHubClient);
    provider.setGitHubProperties(gitHubProperties);
    provider.setMeterRegistry(meterRegistry);
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
  void shouldFailInitializationWithANonPositiveRefreshTimeout() {
    gitHubProperties.setRefreshTimeoutSeconds(0);

    assertThrows(IllegalStateException.class, () -> provider.afterPropertiesSet());
  }

  @Test
  void shouldFailInitializationWithANegativeStalenessLimit() {
    gitHubProperties.setMaxStaleRolesSeconds(-1);

    assertThrows(IllegalStateException.class, () -> provider.afterPropertiesSet());
  }

  @Test
  void shouldFailInitializationWithANonPositiveAlertStaleness() {
    gitHubProperties.setAlertStalenessSeconds(0);

    assertThrows(IllegalStateException.class, () -> provider.afterPropertiesSet());
  }

  @Test
  void shouldFailInitializationWithANegativeWarmInterval() {
    gitHubProperties.setHealthWarmIntervalSeconds(-1);

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
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Arrays.asList(user1));

    // Mock team 2 members
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

    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Collections.emptyList());

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

    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Arrays.asList(user1));

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

    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Arrays.asList(user1));

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

    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Collections.emptyList());

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
  void shouldThrowFor404OrganizationNotFoundWithNothingCached() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // A 404 for the organization (e.g. a renamed org, or a token that cannot see it) must not be
    // read as "the organization has no members"
    when(gitHubClient.getOrganization("test-org"))
        .thenThrow(new GHFileNotFoundException("Organization not found"));

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When & Then
    assertThrows(ProviderException.class, () -> provider.loadRoles(externalUser));
  }

  @Test
  void shouldKeepCachedMembersWhenTheOrganizationIsNotFoundOnRefresh() throws Exception {
    AtomicInteger fetches = new AtomicInteger();
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList())
        .thenAnswer(
            succeedOnceThenFail(
                Arrays.asList(user1), fetches, new GHFileNotFoundException("Not Found")));
    stubTeamMembers();

    assertCachedRolesSurviveFailedRefreshes(fetches);
  }

  @Test
  void shouldKeepCachedTeamsWhenTheOrganizationIsNotFoundOnRefresh() throws Exception {
    AtomicInteger fetches = new AtomicInteger();
    stubOrgMembers();
    when(orgTeams.toList())
        .thenAnswer(
            succeedOnceThenFail(
                Arrays.asList(team1, team2), fetches, new GHFileNotFoundException("Not Found")));
    stubTeamMembers();

    assertCachedRolesSurviveFailedRefreshes(fetches);
  }

  @Test
  void shouldListTeamMembersWithoutLookingUpEachTeam() throws Exception {
    provider.afterPropertiesSet();
    stubOrgMembers();
    stubTeamMembers();

    assertOrgAndTeam1Roles(provider.loadRoles(new ExternalUser().setId("user1")));

    // The teams listing already returned each team, so no per-team lookup is needed: that would
    // cost an extra GitHub call per team per refresh
    verify(organization, never()).getTeamBySlug(anyString());
  }

  @Test
  void shouldQueryTheRateLimitOnlyAfterOrganizationLevelFetches() throws Exception {
    provider.afterPropertiesSet();
    stubOrgMembers();
    stubTeamMembers();

    provider.loadRoles(new ExternalUser().setId("user1"));

    // org members + org teams, not once more per team
    verify(gitHubClient, times(2)).getRateLimit();
  }

  @Test
  void shouldRejectCachedRolesOlderThanTheStalenessLimitUntilARefreshSucceeds() throws Exception {
    gitHubProperties.setMaxStaleRolesSeconds(3600);
    provider.afterPropertiesSet();
    AtomicBoolean gitHubFailing = new AtomicBoolean();
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenAnswer(failWhile(gitHubFailing, Arrays.asList(user1)));
    stubTeamMembers();
    ExternalUser externalUser = new ExternalUser().setId("user1");
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));

    // GitHub starts failing: the next refresh fails, and the limit is counted from then
    gitHubFailing.set(true);
    ticker.advance(Duration.ofSeconds(gitHubProperties.getMembershipCacheTTLSeconds() + 1));
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));

    // Within the limit the cached roles are still served, while refreshes keep failing
    ticker.advance(Duration.ofMinutes(59));
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));

    // Past the limit, resolution fails rather than serving roles GitHub has not confirmed
    ticker.advance(Duration.ofMinutes(2));
    ProviderException exception =
        assertThrows(ProviderException.class, () -> provider.loadRoles(externalUser));
    assertTrue(exception.getMessage().contains("stale"), exception.getMessage());
    assertEquals(1.0, meterRegistry.counter("fiat.github.staleRejections").count());

    // Once GitHub answers again, the next refresh restores resolution
    gitHubFailing.set(false);
    ticker.advance(Duration.ofSeconds(gitHubProperties.getMembershipCacheTTLSeconds() + 1));
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));
  }

  @Test
  void shouldServeCachedRolesThatWereOnlyIdleForLongerThanTheStalenessLimit() throws Exception {
    // In production refreshes run in the background: a read past the refresh interval starts one
    // and is answered from the cache. Hold them back to see what that read is answered with.
    gitHubProperties.setMaxStaleRolesSeconds(3600);
    QueueingExecutorService refreshes = new QueueingExecutorService();
    provider.setExecutor(refreshes);
    provider.afterPropertiesSet();
    stubOrgMembers();
    stubTeamMembers();
    ExternalUser externalUser = new ExternalUser().setId("user1");
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));

    // Nothing reads the cache for two hours (e.g. a replica not running the sync); GitHub is fine
    ticker.advance(Duration.ofHours(2));

    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));
    refreshes.runAll();
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));
  }

  @Test
  void shouldReportTheAgeOfTheCachedOrgMembers() throws Exception {
    provider.afterPropertiesSet();
    assertTrue(Double.isNaN(meterRegistry.get("fiat.github.dataAgeSeconds").gauge().value()));
    stubOrgMembers();
    provider.warmOrgMembers();

    ticker.advance(Duration.ofSeconds(120));

    assertEquals(120.0, meterRegistry.get("fiat.github.dataAgeSeconds").gauge().value());
  }

  @Test
  void shouldServeCachedRolesForAsLongAsGitHubFailsByDefault() throws Exception {
    // The staleness limit is opt-in: once reached it fails every login, not only GitHub roles
    provider.afterPropertiesSet();
    AtomicBoolean gitHubFailing = new AtomicBoolean();
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenAnswer(failWhile(gitHubFailing, Arrays.asList(user1)));
    stubTeamMembers();
    ExternalUser externalUser = new ExternalUser().setId("user1");
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));

    gitHubFailing.set(true);
    ticker.advance(Duration.ofDays(30));

    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));
  }

  @Test
  void shouldInvalidateTheInstallationTokenWhenGitHubRejectsItWith401() throws Exception {
    GitHubAppAuthenticator authenticator = mock(GitHubAppAuthenticator.class);
    provider.setGitHubAppAuthenticator(authenticator);
    provider.afterPropertiesSet();
    // hub4j's message is the response body, which does not mention the status
    when(gitHubClient.getOrganization("test-org"))
        .thenThrow(httpException(401, "{\"message\": \"Bad credentials\"}"));

    assertThrows(
        ProviderException.class, () -> provider.loadRoles(new ExternalUser().setId("user1")));

    verify(authenticator).invalidateInstallationToken();
  }

  @Test
  void shouldKeepTheInstallationTokenWhenOnlyTheMessageMentions401() throws Exception {
    GitHubAppAuthenticator authenticator = mock(GitHubAppAuthenticator.class);
    provider.setGitHubAppAuthenticator(authenticator);
    provider.afterPropertiesSet();
    // Not an HTTP response, so it proves nothing about the token
    when(gitHubClient.getOrganization("test-org"))
        .thenThrow(new IOException("connect to 10.0.0.401 failed"));

    assertThrows(
        ProviderException.class, () -> provider.loadRoles(new ExternalUser().setId("user1")));

    verify(authenticator, never()).invalidateInstallationToken();
  }

  @Test
  void shouldKeepTheInstallationTokenForErrorsOtherThan401() throws Exception {
    GitHubAppAuthenticator authenticator = mock(GitHubAppAuthenticator.class);
    provider.setGitHubAppAuthenticator(authenticator);
    provider.afterPropertiesSet();
    when(gitHubClient.getOrganization("test-org"))
        .thenThrow(httpException(503, "{\"message\": \"Service Unavailable\"}"));

    assertThrows(
        ProviderException.class, () -> provider.loadRoles(new ExternalUser().setId("user1")));

    verify(authenticator, never()).invalidateInstallationToken();
  }

  @ParameterizedTest
  @CsvSource({
    "401, {\"message\": \"Bad credentials\"}, 401",
    "403, {\"message\": \"Resource not accessible by integration\"}, 403",
    "403, {\"message\": \"API rate limit exceeded\"}, 429",
    "429, {\"message\": \"Too many requests\"}, 429",
    "422, {\"message\": \"Validation failed\"}, 4xx",
    "502, {\"message\": \"Bad gateway\"}, 5xx"
  })
  void shouldTagHttpFailuresByStatus(int status, String body, String tag) throws Exception {
    provider.afterPropertiesSet();
    when(gitHubClient.getOrganization("test-org")).thenThrow(httpException(status, body));

    assertThrows(
        ProviderException.class, () -> provider.loadRoles(new ExternalUser().setId("user1")));

    assertEquals(
        1.0,
        meterRegistry.counter("fiat.github.fetchFailures", "op", "members", "status", tag).count());
  }

  @Test
  void shouldTagNotFoundAndConnectionFailures() throws Exception {
    provider.afterPropertiesSet();
    when(gitHubClient.getOrganization("test-org"))
        .thenThrow(new GHFileNotFoundException("Not Found"))
        .thenThrow(new IOException("Connection reset"));
    ExternalUser user = new ExternalUser().setId("user1");

    assertThrows(ProviderException.class, () -> provider.loadRoles(user));
    assertThrows(ProviderException.class, () -> provider.loadRoles(user));

    assertEquals(
        1.0,
        meterRegistry
            .counter("fiat.github.fetchFailures", "op", "members", "status", "404")
            .count());
    assertEquals(
        1.0,
        meterRegistry
            .counter("fiat.github.fetchFailures", "op", "members", "status", "io")
            .count());
  }

  @Test
  void shouldCountFetchFailuresByOperationAndStatus() throws Exception {
    provider.afterPropertiesSet();
    when(gitHubClient.getOrganization("test-org"))
        .thenThrow(httpException(503, "{\"message\": \"Service Unavailable\"}"));

    assertThrows(
        ProviderException.class, () -> provider.loadRoles(new ExternalUser().setId("user1")));

    assertEquals(
        1.0,
        meterRegistry
            .counter("fiat.github.fetchFailures", "op", "members", "status", "5xx")
            .count());
  }

  @Test
  @Timeout(10)
  void shouldFailAColdLoadThatExceedsTheRefreshTimeout() throws Exception {
    gitHubProperties.setRefreshTimeoutSeconds(1);
    provider.afterPropertiesSet();
    CountDownLatch neverReleased = new CountDownLatch(1);
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenAnswer(blockOn(neverReleased));

    ProviderException exception =
        assertThrows(
            ProviderException.class, () -> provider.loadRoles(new ExternalUser().setId("user1")));

    assertTrue(exception.getMessage().contains("Timed out"), exception.getMessage());
  }

  @Test
  @Timeout(10)
  void shouldKeepCachedRolesWhenARefreshExceedsTheRefreshTimeout() throws Exception {
    gitHubProperties.setRefreshTimeoutSeconds(1);
    provider.afterPropertiesSet();
    CountDownLatch neverReleased = new CountDownLatch(1);
    AtomicInteger fetches = new AtomicInteger();
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList())
        .thenAnswer(
            invocation ->
                fetches.incrementAndGet() == 1
                    ? Arrays.asList(user1)
                    : blockOn(neverReleased).answer(invocation));
    stubTeamMembers();
    ExternalUser externalUser = new ExternalUser().setId("user1");
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));

    ticker.advance(Duration.ofSeconds(gitHubProperties.getMembershipCacheTTLSeconds() + 1));

    long started = System.nanoTime();
    assertOrgAndTeam1Roles(provider.loadRoles(externalUser));
    // The read waited for the refresh to time out, so the timeout, not a fast failure, ended it
    assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(1)) >= 0);
    assertEquals(2, fetches.get());
  }

  @Test
  @Timeout(10)
  void shouldCountATimedOutFetchOnceEvenWhenTheInterruptIsWrapped() throws Exception {
    gitHubProperties.setRefreshTimeoutSeconds(1);
    provider.afterPropertiesSet();
    CountDownLatch neverReleased = new CountDownLatch(1);
    CountDownLatch cancelled = new CountDownLatch(1);
    when(organization.listMembers()).thenReturn(orgMembers);
    // As when the interrupt lands in the token mint, which wraps it and clears the flag
    when(orgMembers.toList())
        .thenAnswer(
            invocation -> {
              try {
                neverReleased.await();
              } catch (InterruptedException e) {
                cancelled.countDown();
                throw new IOException(
                    "Failed to look up GitHub App installation",
                    new InterruptedIOException("interrupted"));
              }
              throw new IllegalStateException("released unexpectedly");
            });

    assertThrows(
        ProviderException.class, () -> provider.loadRoles(new ExternalUser().setId("user1")));
    cancelled.await();
    Thread.sleep(200); // let the cancelled fetch finish failing

    assertEquals(
        0.0,
        meterRegistry
            .counter("fiat.github.fetchFailures", "op", "members", "status", "io")
            .count());
  }

  @Test
  @Timeout(10)
  void shouldCountATimedOutFetchOnceAsATimeout() throws Exception {
    gitHubProperties.setRefreshTimeoutSeconds(1);
    provider.afterPropertiesSet();
    CountDownLatch neverReleased = new CountDownLatch(1);
    CountDownLatch cancelled = new CountDownLatch(1);
    when(organization.listMembers()).thenReturn(orgMembers);
    // Like hub4j, the interrupted request surfaces as an InterruptedIOException
    when(orgMembers.toList())
        .thenAnswer(
            invocation -> {
              try {
                neverReleased.await();
              } catch (InterruptedException e) {
                cancelled.countDown();
                throw new InterruptedIOException("interrupted");
              }
              throw new IllegalStateException("released unexpectedly");
            });

    assertThrows(
        ProviderException.class, () -> provider.loadRoles(new ExternalUser().setId("user1")));
    cancelled.await();
    Thread.sleep(200); // let the cancelled fetch finish failing

    assertEquals(
        1.0,
        meterRegistry
            .counter("fiat.github.fetchFailures", "op", "members", "status", "timeout")
            .count());
    assertEquals(
        0.0,
        meterRegistry
            .counter("fiat.github.fetchFailures", "op", "members", "status", "io")
            .count());
  }

  @Test
  void shouldReportUnhealthyUntilAGitHubFetchSucceeds() throws Exception {
    provider.afterPropertiesSet();
    assertFalse(provider.getHealthTracker().isProviderHealthy());

    stubOrgMembers();
    provider.warmOrgMembers();

    assertTrue(provider.getHealthTracker().isProviderHealthy());
  }

  @Test
  void shouldStayUnhealthyWhenWarmingFails() throws Exception {
    provider.afterPropertiesSet();
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenThrow(new IOException("Connection reset"));

    provider.warmOrgMembers();

    assertFalse(provider.getHealthTracker().isProviderHealthy());
  }

  @Test
  void shouldWarmOrgMembersWithoutListingTeams() throws Exception {
    gitHubProperties.setHealthWarmIntervalSeconds(600);
    provider.afterPropertiesSet();
    stubOrgMembers();

    provider.warmOrgMembers();
    ticker.advance(Duration.ofSeconds(601));
    provider.warmOrgMembers();

    // Each warm fetches the members again, so the replica's health tracks GitHub, but nothing
    // else: teams are only fetched when roles are resolved
    verify(organization, times(2)).listMembers();
    verify(organization, never()).listTeams();
  }

  @Test
  void shouldSkipWarmingOrgMembersFetchedWithinTheInterval() throws Exception {
    // e.g. on the replica running the role sync, which refreshes them anyway
    gitHubProperties.setHealthWarmIntervalSeconds(600);
    provider.afterPropertiesSet();
    stubOrgMembers();
    provider.loadRoles(new ExternalUser().setId("nobody"));

    ticker.advance(Duration.ofSeconds(300));
    provider.warmOrgMembers();

    verify(organization, times(1)).listMembers();
  }

  @Test
  void shouldNotWarmOrgMembersWhenHealthChecksAreDisabled() throws Exception {
    // Disabled is the default; a warm interval alone must not start the schedule
    gitHubProperties.setHealthWarmIntervalSeconds(600);

    provider.afterPropertiesSet();
    try {
      assertTrue(
          Thread.getAllStackTraces().keySet().stream()
              .noneMatch(thread -> thread.getName().startsWith("github-roles-warm-")));
    } finally {
      provider.destroy();
    }
  }

  @Test
  void shouldNotRecordHealthWhenARefreshFails() throws Exception {
    provider.afterPropertiesSet();
    AtomicBoolean gitHubFailing = new AtomicBoolean();
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenAnswer(failWhile(gitHubFailing, Arrays.asList(user1)));
    provider.warmOrgMembers();
    long lastSuccess = provider.getHealthTracker().getHealthView().getLastSuccessfulUpdateTime();

    gitHubFailing.set(true);
    Thread.sleep(5); // the tracker uses wall-clock milliseconds
    ticker.advance(Duration.ofSeconds(gitHubProperties.getMembershipCacheTTLSeconds() + 1));
    provider.warmOrgMembers();

    assertEquals(
        lastSuccess, provider.getHealthTracker().getHealthView().getLastSuccessfulUpdateTime());
  }

  @Test
  void shouldWarmOrgMembersOnAScheduleWhenHealthChecksAreEnabled() throws Exception {
    gitHubProperties.setHealthCheckEnabled(true);
    gitHubProperties.setHealthWarmIntervalSeconds(600);
    stubOrgMembers();

    provider.afterPropertiesSet();
    try {
      verify(organization, timeout(5000)).listMembers();
    } finally {
      provider.destroy();
    }
  }

  @Test
  void shouldAlertAfterThreeRefreshIntervalsWithoutASuccessfulFetchByDefault() throws Exception {
    gitHubProperties.setMembershipCacheTTLSeconds(600);
    provider.afterPropertiesSet();

    assertEquals(
        1_800_000L, provider.getHealthTracker().getHealthView().getMaximumStalenessTimeMs());
  }

  @Test
  void shouldAlertNoSoonerThanTwoWarmIntervalsByDefault() throws Exception {
    // Replicas not running the sync only fetch when warming, so a shorter default would flap
    gitHubProperties.setMembershipCacheTTLSeconds(60);
    gitHubProperties.setHealthWarmIntervalSeconds(600);
    provider.afterPropertiesSet();

    assertEquals(
        1_200_000L, provider.getHealthTracker().getHealthView().getMaximumStalenessTimeMs());
  }

  @Test
  void shouldFailInitializationWhenAlertStalenessDoesNotOutlastTheWarmInterval() {
    gitHubProperties.setHealthCheckEnabled(true);
    gitHubProperties.setHealthWarmIntervalSeconds(600);
    gitHubProperties.setAlertStalenessSeconds(300);

    assertThrows(IllegalStateException.class, () -> provider.afterPropertiesSet());
  }

  @Test
  void shouldAlertAfterTheConfiguredStaleness() throws Exception {
    gitHubProperties.setAlertStalenessSeconds(900);
    provider.afterPropertiesSet();

    assertEquals(900_000L, provider.getHealthTracker().getHealthView().getMaximumStalenessTimeMs());
  }

  @Test
  void shouldReportTheRemainingRateLimit() throws Exception {
    provider.afterPropertiesSet();
    stubOrgMembers();
    GHRateLimit rateLimit = mock(GHRateLimit.class);
    GHRateLimit.Record core = mock(GHRateLimit.Record.class);
    when(gitHubClient.getRateLimit()).thenReturn(rateLimit);
    when(rateLimit.getCore()).thenReturn(core);
    when(core.getLimit()).thenReturn(5000);
    when(core.getRemaining()).thenReturn(4321);

    provider.warmOrgMembers();

    assertEquals(4321.0, meterRegistry.get("fiat.github.rateLimitRemaining").gauge().value());
  }

  @Test
  void shouldReportSecondsUntilTheInstallationTokenExpires() throws Exception {
    GitHubAppAuthenticator authenticator = mock(GitHubAppAuthenticator.class);
    when(authenticator.getInstallationTokenExpiry())
        .thenReturn(Instant.now().plus(Duration.ofMinutes(30)));
    provider.setGitHubAppAuthenticator(authenticator);
    provider.afterPropertiesSet();

    double secondsToExpiry = meterRegistry.get("fiat.github.tokenSecondsToExpiry").gauge().value();

    assertTrue(secondsToExpiry > 1790 && secondsToExpiry <= 1800, "was " + secondsToExpiry);
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
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenAnswer(succeedOnceThenFail(Arrays.asList(user1), fetches));
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

  /** Returns {@code value}, or fails like a network error while {@code failing} is set. */
  private static <T> Answer<T> failWhile(AtomicBoolean failing, T value) {
    return invocation -> {
      if (failing.get()) {
        throw new IOException("Connection reset");
      }
      return value;
    };
  }

  /** Blocks until {@code latch} is released or the calling thread is interrupted. */
  private static <T> Answer<T> blockOn(CountDownLatch latch) {
    return invocation -> {
      latch.await();
      throw new IllegalStateException("released unexpectedly");
    };
  }

  /** An HTTP error as hub4j raises it: the message is the response body. */
  private static HttpException httpException(int status, String body) {
    return new HttpException(body, status, "", "https://api.github.com/orgs/test-org");
  }

  /** Stubs the org members as user1 only. */
  private void stubOrgMembers() throws IOException {
    when(organization.listMembers()).thenReturn(orgMembers);
    when(orgMembers.toList()).thenReturn(Arrays.asList(user1));
  }

  /** Stubs team-1 with user1 as its only member, and team-2 with no members. */
  private void stubTeamMembers() throws IOException {
    when(team1.listMembers()).thenReturn(team1Members);
    when(team1Members.toList()).thenReturn(Arrays.asList(user1));
    when(team2.listMembers()).thenReturn(team2Members);
    when(team2Members.toList()).thenReturn(Collections.emptyList());
  }

  /** Holds submitted tasks until the test runs them, like a refresh thread that is behind. */
  private static class QueueingExecutorService extends AbstractExecutorService {
    private final List<Runnable> pending = new ArrayList<>();

    @Override
    public void execute(Runnable command) {
      pending.add(command);
    }

    void runAll() {
      List<Runnable> tasks = new ArrayList<>(pending);
      pending.clear();
      tasks.forEach(Runnable::run);
    }

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return new ArrayList<>(pending);
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }
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

    // Mock successful teams fetch, but listing team-1's members fails with 404
    when(team1.listMembers()).thenThrow(new GHFileNotFoundException("Team not found"));
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
    when(team1.listMembers()).thenThrow(new IOException("401 Unauthorized"));

    // team-2 is only fetched if it is checked before team-1 fails
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
    when(team1.listMembers()).thenThrow(new GHFileNotFoundException("Team not found"));
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
