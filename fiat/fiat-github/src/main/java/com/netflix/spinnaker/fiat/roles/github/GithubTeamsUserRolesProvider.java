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
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRateLimit;
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
 * <p>This provider authenticates users based on their GitHub organization and team memberships.
 * Users must be active members of the configured organization to receive any roles.
 *
 * <p>Uses hub4j/github-api library which provides:
 *
 * <ul>
 *   <li>Automatic pagination of results
 *   <li>Built-in rate limit handling
 *   <li>Type-safe domain models (GHOrganization, GHTeam, GHUser)
 *   <li>Better error messages and exception handling
 * </ul>
 *
 * <p><b>Caching Strategy:</b>
 *
 * <ul>
 *   <li>Organization members: Cached with configurable TTL (default: 5 minutes)
 *   <li>Organization teams: Cached with configurable TTL (default: 5 minutes)
 *   <li>Team memberships: Cached per team with configurable TTL and size limits
 *   <li>Cache refresh happens asynchronously in background to minimize latency
 * </ul>
 *
 * <p><b>Error Handling:</b>
 *
 * <ul>
 *   <li>404 errors (organization/team not found) are logged with specific context
 *   <li>Rate limit errors provide actionable guidance and rate limit URL
 *   <li>Authentication errors (401) suggest credential verification
 *   <li>Permission errors (403) suggest permission and rate limit checks
 *   <li>Rate limit status is tracked and warnings are logged when quota is low
 * </ul>
 *
 * @see GitHubProperties for configuration options
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

  /**
   * Initializes the organization members cache.
   *
   * <p>Caches the set of all active members in the organization. This cache is refreshed
   * asynchronously in the background after the TTL expires, ensuring low-latency responses even
   * during cache refresh.
   *
   * <p><b>Cache Size:</b> Limited to 1 entry (single organization support). If multi-org support is
   * added in the future, this size limit must be increased.
   */
  private void initializeMembersCache() {
    this.membersCache =
        CacheBuilder.newBuilder()
            .maximumSize(1) // Single org support - increase if multi-org is needed
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

  /**
   * Initializes the organization teams cache.
   *
   * <p>Caches all teams in the organization as a map (team slug -> GHTeam). This enables fast
   * lookups when checking team memberships for users. Refresh happens asynchronously.
   *
   * <p><b>Cache Size:</b> Limited to 1 entry (single organization support). If multi-org support is
   * added in the future, this size limit must be increased.
   */
  private void initializeTeamsCache() {
    this.teamsCache =
        CacheBuilder.newBuilder()
            .maximumSize(1) // Single org support - increase if multi-org is needed
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

  /**
   * Initializes the team membership cache.
   *
   * <p>Caches the members of each team individually (keyed by team slug). This is more
   * memory-efficient than caching all team memberships upfront, especially for organizations with
   * many teams.
   *
   * <p><b>Cache Size:</b> Configurable per deployment. Default allows caching multiple teams based
   * on usage patterns. LRU eviction ensures most-accessed teams stay cached.
   */
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
   *
   * <p>By default, hub4j's listMembers() returns all members with state "active". Suspended or
   * pending members are excluded automatically by the GitHub API.
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
      logRateLimitInfo("fetchOrgMembers");
      return memberLogins;

    } catch (GHFileNotFoundException e) {
      log.error("GitHub organization not found: {}", orgName, e);
      // For 404, return empty set (org doesn't exist, no retries will help)
      return Collections.emptySet();
    } catch (IOException e) {
      String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

      // Log specific error types with actionable information
      if (message.contains("rate limit")) {
        log.error(
            "GitHub API rate limit exceeded for organization: {}. Check rate limit at: https://api.github.com/rate_limit",
            orgName,
            e);
      } else if (message.contains("401")) {
        log.error(
            "GitHub authentication failed (401 Unauthorized) for organization: {}. Verify credentials.",
            orgName,
            e);
      } else if (message.contains("403")) {
        log.error(
            "GitHub access forbidden (403) for organization: {}. Check permissions and rate limits.",
            orgName,
            e);
      } else {
        log.error("Failed to fetch members for organization: {}", orgName, e);
      }

      // For authentication/authorization errors (401/403), throw exception instead of returning
      // empty set
      // This allows Guava cache to keep using stale cached values until issue is resolved
      if (message.contains("401") || message.contains("403") || message.contains("rate limit")) {
        throw new RuntimeException(
            "Critical GitHub API error - using cached values if available", e);
      }

      // For other transient errors, return empty set
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
      logRateLimitInfo("fetchOrgTeams");
      return teams;

    } catch (GHFileNotFoundException e) {
      log.error("GitHub organization not found: {}", orgName, e);
      // For 404, return empty map (org doesn't exist, no retries will help)
      return Collections.emptyMap();
    } catch (IOException e) {
      String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

      // Log specific error types with actionable information
      if (message.contains("rate limit")) {
        log.error(
            "GitHub API rate limit exceeded for organization: {}. Check rate limit at: https://api.github.com/rate_limit",
            orgName,
            e);
      } else if (message.contains("401")) {
        log.error(
            "GitHub authentication failed (401 Unauthorized) for organization: {}. Verify credentials.",
            orgName,
            e);
      } else if (message.contains("403")) {
        log.error(
            "GitHub access forbidden (403) for organization: {}. Check permissions and rate limits.",
            orgName,
            e);
      } else {
        log.error("Failed to fetch teams for organization: {}", orgName, e);
      }

      // For authentication/authorization errors (401/403), throw exception instead of returning
      // empty map
      // This allows Guava cache to keep using stale cached values until issue is resolved
      if (message.contains("401") || message.contains("403") || message.contains("rate limit")) {
        throw new RuntimeException(
            "Critical GitHub API error - using cached values if available", e);
      }

      // For other transient errors, return empty map
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
      logRateLimitInfo("fetchTeamMembers");
      return memberLogins;

    } catch (GHFileNotFoundException e) {
      log.error(
          "GitHub team or organization not found: {} in organization: {}",
          teamSlug,
          gitHubProperties.getOrganization(),
          e);
      // For 404, return empty set (team doesn't exist, no retries will help)
      return Collections.emptySet();
    } catch (IOException e) {
      String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

      // Log specific error types with actionable information
      if (message.contains("rate limit")) {
        log.error(
            "GitHub API rate limit exceeded for team: {} in organization: {}. Check rate limit at: https://api.github.com/rate_limit",
            teamSlug,
            gitHubProperties.getOrganization(),
            e);
      } else if (message.contains("401")) {
        log.error(
            "GitHub authentication failed (401 Unauthorized) for team: {} in organization: {}. Verify credentials.",
            teamSlug,
            gitHubProperties.getOrganization(),
            e);
      } else if (message.contains("403")) {
        log.error(
            "GitHub access forbidden (403) for team: {} in organization: {}. Check permissions and rate limits.",
            teamSlug,
            gitHubProperties.getOrganization(),
            e);
      } else {
        log.error(
            "Failed to fetch members for team: {} in organization: {}",
            teamSlug,
            gitHubProperties.getOrganization(),
            e);
      }

      // For authentication/authorization errors (401/403), throw exception instead of returning
      // empty set
      // This allows Guava cache to keep using stale cached values until issue is resolved
      if (message.contains("401") || message.contains("403") || message.contains("rate limit")) {
        throw new RuntimeException(
            "Critical GitHub API error - using cached values if available", e);
      }

      // For other transient errors, return empty set
      return Collections.emptySet();
    }
  }

  /**
   * Logs GitHub API rate limit information for debugging and monitoring.
   *
   * <p>This helps track API usage and identify potential rate limit issues before they occur.
   *
   * @param operation The operation that just completed (for context in logs)
   */
  private void logRateLimitInfo(String operation) {
    try {
      GHRateLimit rateLimit = gitHubClient.getRateLimit();

      // Handle null rate limit (can happen in tests or with certain GitHub API responses)
      if (rateLimit == null) {
        log.debug("Rate limit info not available after {}", operation);
        return;
      }

      GHRateLimit.Record core = rateLimit.getCore();

      // Handle null core record
      if (core == null) {
        log.debug("Rate limit core record not available after {}", operation);
        return;
      }

      log.debug(
          "GitHub rate limit after {}: {}/{} remaining, resets at {}",
          operation,
          core.getRemaining(),
          core.getLimit(),
          core.getResetDate());

      // Warn if rate limit is getting low
      if (core.getRemaining() < core.getLimit() * 0.1) {
        log.warn(
            "GitHub API rate limit is running low: {}/{} remaining ({}%). Resets at {}",
            core.getRemaining(),
            core.getLimit(),
            (core.getRemaining() * 100 / core.getLimit()),
            core.getResetDate());
      }
    } catch (IOException e) {
      log.debug("Could not fetch rate limit info after {}", operation, e);
    } catch (Exception e) {
      log.debug("Unexpected error fetching rate limit info after {}", operation, e);
    }
  }

  /**
   * Loads all roles for a given user based on GitHub organization and team memberships.
   *
   * <p><b>Role Assignment Logic:</b>
   *
   * <ol>
   *   <li>Checks if user is an active member of the configured organization
   *   <li>If not a member, returns empty list (no roles)
   *   <li>If member, assigns organization role (e.g., "my-org")
   *   <li>Checks membership in each team within the organization
   *   <li>Assigns team role for each team the user belongs to (e.g., "team-developers")
   * </ol>
   *
   * <p><b>Example:</b> For user "john" in organization "acme" who is member of teams "developers"
   * and "reviewers", returns roles: ["acme", "developers", "reviewers"]
   *
   * <p><b>Note:</b> All role names are converted to lowercase for consistency.
   *
   * @param user External user to load roles for (must have a valid ID/username)
   * @return List of roles (organization + teams), or empty list if not an org member
   */
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

  /**
   * Loads roles for multiple users in a single operation.
   *
   * <p>This is a convenience method that calls {@link #loadRoles(ExternalUser)} for each user. The
   * GitHub API cache is shared across all users, so this is efficient for batch operations.
   *
   * <p><b>Performance:</b> Cached data (org members, teams) is reused across all users in the
   * batch, making this much more efficient than individual calls for large user sets.
   *
   * @param users Collection of external users to load roles for
   * @return Map of user ID to their collection of roles, or empty map if input is null/empty
   */
  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
    if (users == null || users.isEmpty()) {
      return new HashMap<>();
    }

    val emailGroupsMap = new HashMap<String, Collection<Role>>();
    users.forEach(u -> emailGroupsMap.put(u.getId(), loadRoles(u)));

    return emailGroupsMap;
  }

  /**
   * Invalidates all caches (members, teams, and team memberships).
   *
   * <p>This method is primarily intended for testing purposes to force fresh fetches from the
   * GitHub API. In production, the caches refresh automatically based on the configured refresh
   * intervals.
   *
   * <p><b>Use Cases:</b>
   *
   * <ul>
   *   <li>Testing error scenarios that require fresh API calls
   *   <li>Manual cache clearing during debugging
   *   <li>Forcing cache refresh after configuration changes
   * </ul>
   */
  public void invalidateAll() {
    if (membersCache != null) {
      membersCache.invalidateAll();
    }
    if (teamsCache != null) {
      teamsCache.invalidateAll();
    }
    if (teamMembershipCache != null) {
      teamMembershipCache.invalidateAll();
    }
    log.debug("All GitHub caches invalidated (members, teams, team memberships)");
  }

  /**
   * Invalidates the organization members cache.
   *
   * <p>This forces a fresh fetch of organization members on the next access. Team and team
   * membership caches are not affected.
   */
  public void invalidateMembersCache() {
    if (membersCache != null) {
      membersCache.invalidateAll();
      log.debug("GitHub members cache invalidated");
    }
  }

  /**
   * Invalidates the organization teams cache.
   *
   * <p>This forces a fresh fetch of organization teams on the next access. Members and team
   * membership caches are not affected.
   */
  public void invalidateTeamsCache() {
    if (teamsCache != null) {
      teamsCache.invalidateAll();
      log.debug("GitHub teams cache invalidated");
    }
  }

  /**
   * Invalidates the team memberships cache.
   *
   * <p>This forces a fresh fetch of team memberships on the next access. Members and teams caches
   * are not affected.
   */
  public void invalidateTeamMembershipsCache() {
    if (teamMembershipCache != null) {
      teamMembershipCache.invalidateAll();
      log.debug("GitHub team memberships cache invalidated");
    }
  }

  /**
   * Invalidates a specific team's membership cache.
   *
   * <p>This allows selective cache invalidation for a single team without affecting other cached
   * data.
   *
   * @param teamSlug The team slug to invalidate
   */
  public void invalidateTeamMembership(String teamSlug) {
    if (teamMembershipCache != null && teamSlug != null) {
      teamMembershipCache.invalidate(teamSlug);
      log.debug("GitHub team membership cache invalidated for team: {}", teamSlug);
    }
  }

  /**
   * Returns the current size of all caches combined.
   *
   * <p>This method is primarily intended for monitoring and testing purposes.
   *
   * @return Total number of cached entries across all caches
   */
  public long getCacheSize() {
    long size = 0;
    if (membersCache != null) {
      size += membersCache.size();
    }
    if (teamsCache != null) {
      size += teamsCache.size();
    }
    if (teamMembershipCache != null) {
      size += teamMembershipCache.size();
    }
    return size;
  }
}
