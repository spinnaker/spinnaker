/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GitHub;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * User roles provider that fetches team memberships from GitHub.
 *
 * <p>Uses hub4j/github-api library which provides:
 *
 * <ul>
 *   <li>Automatic pagination of results
 *   <li>Built-in rate limit handling
 *   <li>Type-safe domain models (GHOrganization, GHTeam, GHUser)
 *   <li>Better error messages and exception handling
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "github")
public class GithubTeamsUserRolesProvider implements UserRolesProvider, InitializingBean {

  @Autowired @Setter private GitHub gitHubClient;

  @Autowired @Setter private GitHubProperties gitHubProperties;

  private ExecutorService executor = Executors.newSingleThreadExecutor();

  private LoadingCache<String, Set<String>> membersCache;

  private LoadingCache<String, Map<String, GHTeam>> teamsCache;

  private LoadingCache<String, Set<String>> teamMembershipCache;

  @Override
  public void afterPropertiesSet() throws Exception {
    Assert.state(gitHubProperties.getOrganization() != null, "Supply an organization");
    Assert.state(gitHubProperties.getBaseUrl() != null, "Supply a base url");

    this.initializeMembersCache();
    this.initializeTeamsCache();
    this.initializeTeamMembershipCache();
  }

  private void initializeMembersCache() {
    // Note if multiple github orgs is ever supported the maximumSize will need to change
    this.membersCache =
        CacheBuilder.newBuilder()
            .maximumSize(1) // This will only be a cache of one entry keyed by org name.
            .refreshAfterWrite(
                this.gitHubProperties.getMembershipCacheTTLSeconds(), TimeUnit.SECONDS)
            .build(
                new CacheLoader<String, Set<String>>() {
                  public Set<String> load(String key) {
                    return fetchOrgMembers(key);
                  }

                  public ListenableFuture<Set<String>> reload(
                      final String key, final Set<String> prev) {
                    ListenableFutureTask<Set<String>> task =
                        ListenableFutureTask.create(() -> load(key));
                    executor.execute(task);
                    return task;
                  }
                });
  }

  private void initializeTeamsCache() {
    // Note if multiple github orgs is ever supported the maximumSize will need to change
    this.teamsCache =
        CacheBuilder.newBuilder()
            .maximumSize(1) // This will only be a cache of one entry keyed by org name.
            .refreshAfterWrite(
                this.gitHubProperties.getMembershipCacheTTLSeconds(), TimeUnit.SECONDS)
            .build(
                new CacheLoader<String, Map<String, GHTeam>>() {
                  public Map<String, GHTeam> load(String key) {
                    return fetchOrgTeams(key);
                  }

                  public ListenableFuture<Map<String, GHTeam>> reload(
                      final String key, final Map<String, GHTeam> prev) {
                    ListenableFutureTask<Map<String, GHTeam>> task =
                        ListenableFutureTask.create(() -> load(key));
                    executor.execute(task);
                    return task;
                  }
                });
  }

  private void initializeTeamMembershipCache() {
    this.teamMembershipCache =
        CacheBuilder.newBuilder()
            .maximumSize(this.gitHubProperties.getMembershipCacheTeamsSize())
            .refreshAfterWrite(
                this.gitHubProperties.getMembershipCacheTTLSeconds(), TimeUnit.SECONDS)
            .build(
                new CacheLoader<String, Set<String>>() {
                  public Set<String> load(String teamSlug) {
                    return fetchTeamMembers(teamSlug);
                  }

                  public ListenableFuture<Set<String>> reload(
                      final String key, final Set<String> prev) {
                    ListenableFutureTask<Set<String>> task =
                        ListenableFutureTask.create(() -> load(key));
                    executor.execute(task);
                    return task;
                  }
                });
  }

  /**
   * Fetches organization members using hub4j. Pagination is handled automatically by PagedIterable.
   */
  private Set<String> fetchOrgMembers(String orgName) {
    try {
      log.debug("Fetching members for organization: {}", orgName);

      GHOrganization org = gitHubClient.getOrganization(orgName);

      // hub4j handles pagination automatically!
      Set<String> memberLogins =
          org.listMembers().toList().stream()
              .map(user -> user.getLogin().toLowerCase())
              .collect(Collectors.toSet());

      log.info("Fetched {} members from organization {}", memberLogins.size(), orgName);
      return memberLogins;

    } catch (IOException e) {
      log.error("Failed to fetch members for organization: {}", orgName, e);
      return Collections.emptySet();
    }
  }

  /** Fetches organization teams using hub4j. Returns a map of slug -> GHTeam for easy lookup. */
  private Map<String, GHTeam> fetchOrgTeams(String orgName) {
    try {
      log.debug("Fetching teams for organization: {}", orgName);

      GHOrganization org = gitHubClient.getOrganization(orgName);
      Map<String, GHTeam> teams = org.getTeams();

      log.info("Fetched {} teams from organization {}", teams.size(), orgName);
      return teams;

    } catch (IOException e) {
      log.error("Failed to fetch teams for organization: {}", orgName, e);
      return Collections.emptyMap();
    }
  }

  /** Fetches members of a specific team using hub4j. */
  private Set<String> fetchTeamMembers(String teamSlug) {
    try {
      log.debug(
          "Fetching members for team: {} in organization: {}",
          teamSlug,
          gitHubProperties.getOrganization());

      GHOrganization org = gitHubClient.getOrganization(gitHubProperties.getOrganization());
      GHTeam team = org.getTeamBySlug(teamSlug);

      if (team == null) {
        log.warn(
            "Team not found: {} in organization: {}", teamSlug, gitHubProperties.getOrganization());
        return Collections.emptySet();
      }

      // hub4j handles pagination automatically!
      Set<String> memberLogins =
          team.listMembers().toList().stream()
              .map(user -> user.getLogin().toLowerCase())
              .collect(Collectors.toSet());

      log.debug("Fetched {} members for team {}", memberLogins.size(), teamSlug);
      return memberLogins;

    } catch (IOException e) {
      log.error(
          "Failed to fetch members for team: {} in organization: {}",
          teamSlug,
          gitHubProperties.getOrganization(),
          e);
      return Collections.emptySet();
    }
  }

  @Override
  public List<Role> loadRoles(ExternalUser user) {
    String username = user.getId();

    log.debug("Loading roles for user: {}", username);

    if (StringUtils.isEmpty(username) || StringUtils.isEmpty(gitHubProperties.getOrganization())) {
      return new ArrayList<>();
    }

    if (!isMemberOfOrg(username)) {
      log.debug(
          "User {} is not a member of organization {}",
          username,
          gitHubProperties.getOrganization());
      return new ArrayList<>();
    }

    log.debug(
        "User {} is a member of organization {}", username, gitHubProperties.getOrganization());

    List<Role> result = new ArrayList<>();
    result.add(toRole(gitHubProperties.getOrganization()));

    // Get teams of the org
    Map<String, GHTeam> teams = getTeams();
    log.debug("Found {} teams in organization", teams.size());

    teams
        .values()
        .forEach(
            team -> {
              String teamSlug = team.getSlug();
              String teamName = team.getName();
              String debugMsg = username + " is a member of team " + teamName;

              if (isMemberOfTeam(teamSlug, username)) {
                result.add(toRole(teamSlug));
                debugMsg += ": true";
              } else {
                debugMsg += ": false";
              }
              log.debug(debugMsg);
            });

    return result;
  }

  private boolean isMemberOfOrg(String username) {
    try {
      return this.membersCache
          .get(gitHubProperties.getOrganization())
          .contains(username.toLowerCase());
    } catch (ExecutionException e) {
      log.error("Failed to read from cache when getting org membership", e);
      return false;
    }
  }

  private Map<String, GHTeam> getTeams() {
    try {
      return this.teamsCache.get(gitHubProperties.getOrganization());
    } catch (ExecutionException e) {
      log.error("Failed to read from cache when getting teams", e);
      return Collections.emptyMap();
    }
  }

  private boolean isMemberOfTeam(String teamSlug, String username) {
    try {
      return this.teamMembershipCache.get(teamSlug).contains(username.toLowerCase());
    } catch (ExecutionException e) {
      log.error("Failed to read from cache when getting team membership", e);
      return false;
    }
  }

  private static Role toRole(String name) {
    return new Role().setName(name.toLowerCase()).setSource(Role.Source.GITHUB_TEAMS);
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
    if (users == null || users.isEmpty()) {
      return new HashMap<>();
    }

    val emailGroupsMap = new HashMap<String, Collection<Role>>();
    users.forEach(u -> emailGroupsMap.put(u.getId(), loadRoles(u)));

    return emailGroupsMap;
  }
}
