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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

/**
 * Integration tests for GithubTeamsUserRolesProvider using WireMock to mock the GitHub API.
 *
 * <p>These tests verify the full HTTP integration stack with a real hub4j GitHub client making
 * actual HTTP calls to WireMock. This complements the unit tests in {@link
 * GithubTeamsUserRolesProviderTest}.
 *
 * <p><b>Note:</b> The hub4j GitHub client makes multiple internal API calls. All endpoints must be
 * properly stubbed to prevent test hangs.
 *
 * @see GithubTeamsUserRolesProviderTest for unit tests with mocked GitHub client
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class GithubTeamsUserRolesProviderIntegrationTest {

  private static final String TEST_ORG = "test-org";
  private static final String TEST_TOKEN = "ghp_test_token";

  @RegisterExtension
  static final WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private GithubTeamsUserRolesProvider provider;
  private GitHubProperties gitHubProperties;

  @BeforeEach
  void setUp() throws Exception {
    wireMock.resetAll();

    // IMPORTANT: Add catch-all stub for any unstubbed request to fail fast
    // This prevents tests from hanging on unexpected API calls
    wireMock.stubFor(
        any(anyUrl())
            .atPriority(100) // Lowest priority - only matches if nothing else does
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\": \"Not Found - unstubbed endpoint\"}")));

    // Stub rate limit endpoint (called by hub4j internally)
    stubRateLimitEndpoint();

    // Create a real GitHub client pointing to WireMock
    GitHub gitHubClient =
        new GitHubBuilder().withEndpoint(wireMock.baseUrl()).withOAuthToken(TEST_TOKEN).build();

    gitHubProperties = new GitHubProperties();
    gitHubProperties.setBaseUrl(wireMock.baseUrl());
    gitHubProperties.setOrganization(TEST_ORG);
    gitHubProperties.setAccessToken(TEST_TOKEN);

    provider = new GithubTeamsUserRolesProvider();
    provider.setGitHubClient(gitHubClient);
    provider.setGitHubProperties(gitHubProperties);
    provider.afterPropertiesSet();
  }

  @AfterEach
  void tearDown() {
    if (provider != null) {
      provider.invalidateAll();
    }
  }

  // ===== Happy Path Tests =====

  @Test
  void shouldLoadRolesForOrgMemberWithTeams() throws Exception {
    // Given - Stub all required GitHub API endpoints
    stubOrgEndpoint();
    stubOrgMembersEndpoint(
        "[" + userJson("alice") + ", " + userJson("bob") + "]");
    stubOrgTeamsEndpoint(
        "[" + teamJson("developers", "Developers", 1) + ", " + teamJson("admins", "Admins", 2) + "]");
    stubTeamBySlugEndpoint("developers", teamJson("developers", "Developers", 1));
    stubTeamBySlugEndpoint("admins", teamJson("admins", "Admins", 2));
    // hub4j uses /organizations/{org_id}/team/{team_id}/members endpoint
    stubTeamMembersEndpointById(1, "[" + userJson("alice") + "]");
    stubTeamMembersEndpointById(2, "[]");

    ExternalUser externalUser = new ExternalUser().setId("alice");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    assertNotNull(roles);
    assertEquals(2, roles.size()); // org + developers team

    assertTrue(
        roles.stream().anyMatch(r -> r.getName().equals(TEST_ORG)),
        "Should have org role");
    assertTrue(
        roles.stream().anyMatch(r -> r.getName().equals("developers")),
        "Should have developers team role");
    assertFalse(
        roles.stream().anyMatch(r -> r.getName().equals("admins")),
        "Should NOT have admins team role");

    // Verify all roles have correct source
    roles.forEach(role -> assertEquals(Role.Source.GITHUB_TEAMS, role.getSource()));

    // Verify HTTP calls were made
    wireMock.verify(getRequestedFor(urlPathEqualTo("/orgs/" + TEST_ORG)));
    wireMock.verify(getRequestedFor(urlPathEqualTo("/orgs/" + TEST_ORG + "/members")));
  }

  @Test
  void shouldReturnEmptyRolesForNonOrgMember() throws Exception {
    // Given
    stubOrgEndpoint();
    stubOrgMembersEndpoint("[" + userJson("alice") + "]");

    ExternalUser externalUser = new ExternalUser().setId("charlie"); // Not in org

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    assertNotNull(roles);
    assertTrue(roles.isEmpty(), "Non-member should have no roles");
  }

  @Test
  void shouldHandleCaseInsensitiveUsernames() throws Exception {
    // Given - GitHub returns uppercase username
    stubOrgEndpoint();
    stubOrgMembersEndpoint("[" + userJson("ALICE") + "]");
    stubOrgTeamsEndpoint("[]");

    ExternalUser externalUser = new ExternalUser().setId("alice"); // lowercase

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then - Should match case-insensitively
    assertNotNull(roles);
    assertEquals(1, roles.size());
    assertEquals(TEST_ORG, roles.get(0).getName());
  }

  // ===== Error Handling Tests =====

  @Test
  void shouldHandleOrganizationNotFound() throws Exception {
    // Given - 404 for organization (override the default org stub)
    wireMock.stubFor(
        get(urlPathEqualTo("/orgs/" + TEST_ORG))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\": \"Not Found\"}")));

    ExternalUser externalUser = new ExternalUser().setId("alice");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then - Should return empty (org doesn't exist)
    assertNotNull(roles);
    assertTrue(roles.isEmpty());
  }

  @Test
  void shouldReturnEmptyRolesForUnauthorizedError() throws Exception {
    // Given - 401 Unauthorized
    // Note: With real hub4j client, 401 errors result in HttpException
    // which is caught and handled as "401" in the error message
    wireMock.stubFor(
        get(urlPathEqualTo("/orgs/" + TEST_ORG))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\": \"Bad credentials\"}")));

    ExternalUser externalUser = new ExternalUser().setId("alice");

    // When - hub4j HttpException is caught by the provider
    // The cache will either throw or return empty depending on cache state
    // For fresh cache, it returns empty roles
    List<Role> roles = provider.loadRoles(externalUser);

    // Then - Should return empty roles for auth errors on fresh cache
    assertNotNull(roles);
    assertTrue(roles.isEmpty());
  }

  // Note: Rate limit testing is not practical with WireMock because hub4j has
  // built-in rate limit retry logic that waits for the reset time.
  // Rate limit error handling is tested in unit tests instead.

  // ===== Rate Limit Tests =====

  @Test
  void shouldHandleZeroRateLimitGracefully() throws Exception {
    // Given - Rate limit is 0 (edge case that could cause division by zero)
    stubRateLimitEndpointWithValues(0, 0);
    stubOrgEndpoint();
    stubOrgMembersEndpoint("[" + userJson("alice") + "]");
    stubOrgTeamsEndpoint("[]");

    ExternalUser externalUser = new ExternalUser().setId("alice");

    // When - Should not throw division by zero
    List<Role> roles = provider.loadRoles(externalUser);

    // Then
    assertNotNull(roles);
    assertEquals(1, roles.size());
  }

  // ===== Multi-User Tests =====

  @Test
  void shouldLoadRolesForMultipleUsers() throws Exception {
    // Given
    stubOrgEndpoint();
    stubOrgMembersEndpoint(
        "[" + userJson("alice") + ", " + userJson("bob") + "]");
    stubOrgTeamsEndpoint(
        "[" + teamJson("team-a", "Team A", 1) + "]");
    stubTeamBySlugEndpoint("team-a", teamJson("team-a", "Team A", 1));
    stubTeamMembersEndpointById(1, "[" + userJson("alice") + "]");

    Collection<ExternalUser> users =
        Arrays.asList(new ExternalUser().setId("alice"), new ExternalUser().setId("bob"));

    // When
    Map<String, Collection<Role>> rolesMap = provider.multiLoadRoles(users);

    // Then
    assertNotNull(rolesMap);
    assertEquals(2, rolesMap.size());

    Collection<Role> aliceRoles = rolesMap.get("alice");
    assertEquals(2, aliceRoles.size()); // org + team-a

    Collection<Role> bobRoles = rolesMap.get("bob");
    assertEquals(1, bobRoles.size()); // org only
  }

  // ===== JSON Response Templates =====

  private static String userJson(String login) {
    return String.format(
        "{\"login\": \"%s\", \"id\": %d, \"node_id\": \"MDQ6VXNlcjE=\", "
            + "\"avatar_url\": \"https://avatars.githubusercontent.com/u/1\", "
            + "\"type\": \"User\", \"site_admin\": false}",
        login, Math.abs(login.hashCode()));
  }

  private static String orgJson(String orgName) {
    return String.format(
        "{\"login\": \"%s\", \"id\": 12345, \"node_id\": \"MDEyOk9yZ2FuaXphdGlvbjE=\", "
            + "\"url\": \"https://api.github.com/orgs/%s\", "
            + "\"repos_url\": \"https://api.github.com/orgs/%s/repos\", "
            + "\"events_url\": \"https://api.github.com/orgs/%s/events\", "
            + "\"hooks_url\": \"https://api.github.com/orgs/%s/hooks\", "
            + "\"issues_url\": \"https://api.github.com/orgs/%s/issues\", "
            + "\"members_url\": \"https://api.github.com/orgs/%s/members{/member}\", "
            + "\"public_members_url\": \"https://api.github.com/orgs/%s/public_members{/member}\", "
            + "\"avatar_url\": \"https://avatars.githubusercontent.com/u/12345\", "
            + "\"description\": \"Test organization\", "
            + "\"type\": \"Organization\"}",
        orgName, orgName, orgName, orgName, orgName, orgName, orgName, orgName);
  }

  private String teamJson(String slug, String name, int id) {
    // Include the organization in the team JSON to help hub4j resolve the correct endpoints
    return String.format(
        "{\"id\": %d, \"node_id\": \"MDQ6VGVhbTE=\", \"name\": \"%s\", \"slug\": \"%s\", "
            + "\"description\": \"Test team\", \"privacy\": \"closed\", "
            + "\"permission\": \"pull\", "
            + "\"members_url\": \"%s/organizations/12345/team/%d/members{/member}\", "
            + "\"repositories_url\": \"%s/teams/%d/repos\", "
            + "\"organization\": " + orgJson(TEST_ORG) + "}",
        id, name, slug, wireMock.baseUrl(), id, wireMock.baseUrl(), id);
  }

  private static String rateLimitJson(int remaining, int limit) {
    long resetTime = System.currentTimeMillis() / 1000 + 3600;
    return String.format(
        "{\"resources\": {"
            + "\"core\": {\"limit\": %d, \"remaining\": %d, \"reset\": %d, \"used\": %d},"
            + "\"search\": {\"limit\": 30, \"remaining\": 30, \"reset\": %d, \"used\": 0},"
            + "\"graphql\": {\"limit\": 5000, \"remaining\": 5000, \"reset\": %d, \"used\": 0}"
            + "}, \"rate\": {\"limit\": %d, \"remaining\": %d, \"reset\": %d, \"used\": %d}}",
        limit, remaining, resetTime, limit - remaining,
        resetTime, resetTime,
        limit, remaining, resetTime, limit - remaining);
  }

  // ===== Stubbing Helper Methods =====

  private void stubRateLimitEndpoint() {
    stubRateLimitEndpointWithValues(4500, 5000);
  }

  private void stubRateLimitEndpointWithValues(int remaining, int limit) {
    wireMock.stubFor(
        get(urlPathEqualTo("/rate_limit"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(rateLimitJson(remaining, limit))));
  }

  private void stubOrgEndpoint() {
    wireMock.stubFor(
        get(urlPathEqualTo("/orgs/" + TEST_ORG))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(orgJson(TEST_ORG))));
  }

  private void stubOrgMembersEndpoint(String responseBody) {
    wireMock.stubFor(
        get(urlPathEqualTo("/orgs/" + TEST_ORG + "/members"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)));
  }

  private void stubOrgTeamsEndpoint(String responseBody) {
    wireMock.stubFor(
        get(urlPathEqualTo("/orgs/" + TEST_ORG + "/teams"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)));
  }

  private void stubTeamBySlugEndpoint(String teamSlug, String responseBody) {
    wireMock.stubFor(
        get(urlPathEqualTo("/orgs/" + TEST_ORG + "/teams/" + teamSlug))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)));
  }

  private void stubTeamMembersEndpoint(String teamSlug, String responseBody) {
    wireMock.stubFor(
        get(urlPathEqualTo("/orgs/" + TEST_ORG + "/teams/" + teamSlug + "/members"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)));
  }

  private void stubTeamMembersEndpointById(int teamId, String responseBody) {
    // hub4j calls /organizations/{org_id}/team/{team_id}/members for team member listing
    wireMock.stubFor(
        get(urlPathEqualTo("/organizations/12345/team/" + teamId + "/members"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)));
  }
}
