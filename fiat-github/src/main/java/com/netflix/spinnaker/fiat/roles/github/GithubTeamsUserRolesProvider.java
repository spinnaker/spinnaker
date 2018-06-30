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
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubClient;
import com.netflix.spinnaker.fiat.roles.github.model.Team;
import com.netflix.spinnaker.fiat.roles.github.model.TeamMembership;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.groupMembership.service", havingValue = "github")
public class GithubTeamsUserRolesProvider implements UserRolesProvider, InitializingBean {

  private static List<String> RATE_LIMITING_HEADERS = Arrays.asList(
      "X-RateLimit-Limit",
      "X-RateLimit-Remaining",
      "X-RateLimit-Reset"
  );

  @Autowired
  @Setter
  private GitHubClient gitHubClient;

  @Autowired
  @Setter
  private GitHubProperties gitHubProperties;

  private LoadingCache<CacheKey, Boolean> teamMembershipCache;

  private static final String ACTIVE = "active";

  @Override
  public void afterPropertiesSet() throws Exception {
    Assert.state(gitHubProperties.getOrganization() != null, "Supply an organization");
    Assert.state(gitHubProperties.getBaseUrl() != null, "Supply a base url");

    this.initializeTeamMembershipCache();
  }

  private void initializeTeamMembershipCache() {
    this.teamMembershipCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(this.gitHubProperties.getMembershipCacheTTLSeconds(), TimeUnit.SECONDS)
        .build(
            new CacheLoader<CacheKey, Boolean>() {
              public Boolean load(CacheKey key) {
                try {
                  TeamMembership response = gitHubClient.isMemberOfTeam(key.getTeamId(), key.getUsername());
                  return (response.getState().equals(GithubTeamsUserRolesProvider.ACTIVE));
                } catch (RetrofitError e) {
                  if (e.getResponse().getStatus() != 404) {
                    handleNon404s(e);
                  }
                }
                return false;
              }
            });
  }

  @Override
  public List<Role> loadRoles(ExternalUser user) {
    String username = user.getId();

    log.debug("loadRoles for user " + username);
    if (StringUtils.isEmpty(username) || StringUtils.isEmpty(gitHubProperties.getOrganization())) {
      return new ArrayList<>();
    }

    if (!isMemberOfOrg(username)) {
      log.debug(username + "is not a member of organization " + gitHubProperties.getOrganization());
      return new ArrayList<>();
    }
    log.debug(username + "is a member of organization " + gitHubProperties.getOrganization());

    List<Role> result = new ArrayList<>();
    result.add(toRole(gitHubProperties.getOrganization()));

    // Get teams of the org
    List<Team> teams = getTeams();
    log.debug("Found " + teams.size() + " teams in org.");

    teams.forEach(t -> {
      String debugMsg = username + " is a member of team " + t.getName();
      if (isMemberOfTeam(t, username)) {
        result.add(toRole(t.getSlug()));
        debugMsg += ": true";
      } else {
        debugMsg += ": false";
      }
      log.debug(debugMsg);
    });

    return result;
  }

  private boolean isMemberOfOrg(String username) {
    boolean isMemberOfOrg = false;
    try {
      Response response = gitHubClient.isMemberOfOrganization(gitHubProperties.getOrganization(),
                                                              username);
      isMemberOfOrg = (response.getStatus() == 204);
    } catch (RetrofitError e) {
      if (e.getResponse().getStatus() != 404) {
        handleNon404s(e);
      }
    }

    return isMemberOfOrg;
  }

  private List<Team> getTeams() {
    List<Team> teams = new ArrayList<>();
    int page = 1;
    boolean hasMorePages = true;

    do {
      List<Team> teamsPage = getTeamsInOrgPaginated(page++);
      teams.addAll(teamsPage);
      if (teamsPage.size() != gitHubProperties.paginationValue) {
        hasMorePages = false;
      }
      log.debug("Got " + teamsPage.size() + " teams back. hasMorePages: " + hasMorePages);
    } while (hasMorePages);

    return teams;
  }

  private List<Team> getTeamsInOrgPaginated(int page) {
    List<Team> teams = new ArrayList<>();
    try {
      log.debug("Requesting page " + page + " of teams.");
      teams = gitHubClient.getOrgTeams(gitHubProperties.getOrganization(),
                                       page,
                                       gitHubProperties.paginationValue);
    } catch (RetrofitError e) {
      if (e.getResponse().getStatus() != 404) {
        handleNon404s(e);
      } else {
        log.error("404 when getting teams", e);
      }
    }

    return teams;
  }

  private boolean isMemberOfTeam(Team t, String username) {
    try {
      return this.teamMembershipCache.get(new CacheKey(t.getId(), username));
    } catch (ExecutionException e) {
      log.error("Failed to read from cache when getting team membership", e);
    }
    return false;
  }

  private void handleNon404s(RetrofitError e) {
    String msg = "";
    if (e.getKind() == RetrofitError.Kind.NETWORK) {
      msg = String.format("Could not find the server %s", gitHubProperties.getBaseUrl());
    } else if (e.getResponse().getStatus() == 401) {
      msg = "HTTP 401 Unauthorized.";
    } else if (e.getResponse().getStatus() == 403) {
      val rateHeaders = e.getResponse()
                         .getHeaders()
                         .stream()
                         .filter(header -> RATE_LIMITING_HEADERS.contains(header.getName()))
                         .map(Header::toString)
                         .collect(Collectors.toList());

      msg = "HTTP 403 Forbidden. Rate limit info: " + StringUtils.join(rateHeaders, ", ");
    }
    log.error(msg, e);
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

  @Data
  private class CacheKey {
    private final Long teamId;
    private final String username;
  }
}
