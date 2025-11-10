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
  void shouldHandleIOExceptionFromGitHub() throws Exception {
    // Given
    provider.afterPropertiesSet();

    // Mock IOException when getting organization
    when(gitHubClient.getOrganization("test-org")).thenThrow(new IOException("API error"));

    ExternalUser externalUser = new ExternalUser().setId("user1");

    // When
    List<Role> roles = provider.loadRoles(externalUser);

    // Then - Should handle gracefully and return empty roles
    assertNotNull(roles);
    assertTrue(roles.isEmpty());
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
}
