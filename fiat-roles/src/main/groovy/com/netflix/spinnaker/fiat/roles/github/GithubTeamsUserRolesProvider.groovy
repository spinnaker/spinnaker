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

package com.netflix.spinnaker.fiat.roles.github

import com.netflix.spinnaker.fiat.roles.UserRolesProvider
import com.netflix.spinnaker.fiat.roles.github.client.GitHubMaster
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.util.Assert
import retrofit.RetrofitError
import retrofit.client.Response

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.groupMembership.service", havingValue = "github")
class GithubTeamsUserRolesProvider implements UserRolesProvider, InitializingBean {

  @Autowired
  GitHubMaster master

  @Autowired
  GitHubProperties gitHubProperties

  @Override
  List<String> loadRoles(String userName) {
    if (!userName || !gitHubProperties.organization) {
      return []
    }
    // check organization if set.
    // If organization is unset, all GitHub users can login and have full access
    // If an organization is set, add it to roles to restrict users to this organization
    // If organization is set AND requiredGroupMembership set, organization members will have RO access
    // and requiredGroupMembership members RW access
    Boolean isMemberOfOrg = false

    try {
      Response response = master.gitHubClient.isMemberOfOrganization(gitHubProperties.organization, userName)
      isMemberOfOrg = (response.status == 204)
    } catch (RetrofitError e) {
      if (e.getKind() == RetrofitError.Kind.NETWORK) {
        log.error("Could not find the server ${master.baseUrl}", e)
        return []
      } else if (e.response.status == 404) {
        log.error("Could not find the GitHub organization ${gitHubProperties.organization}", e)
        return []
      } else if (e.response.status == 401) {
        log.error("Cannot get GitHub organization ${gitHubProperties.organization} informations: Not authorized.", e)
        return []
      }
    }

    if (!isMemberOfOrg) {
      return []
    }

    List result = []
    result.add(gitHubProperties.organization)

    // Get teams of the current user
    List<GitHubMaster.Team> teams
    try {
      teams = master.gitHubClient.getOrgTeams(gitHubProperties.organization)

    } catch (RetrofitError e) {
      log.error("RetrofitError ${e.response.status} ${e.response.reason} ", e)
      if (e.getKind() == RetrofitError.Kind.NETWORK) {
        log.error("Could not find the server ${master.baseUrl}", e)
      } else if (e.response.status == 404) {
        log.error(" 404 when getting teams")
        return result
      } else if (e.response.status == 401) {
        log.error("Cannot get GitHub organization ${gitHubProperties.organization} teams: Not authorized.", e)
        return result
      }
    }
    if (teams) {
      teams.each { GitHubMaster.Team t ->
        if (isMemberOfTeam(t, userName)) {
          result.add(t.slug)
        }
      }
    }

    return result
  }


  boolean isMemberOfTeam(GitHubMaster.Team t, String userName) {
    String ACTIVE = "active"
    try {
      GitHubMaster.TeamMembership response = master.gitHubClient.isMemberOfTeam(t.id, userName)
      return (response.state == ACTIVE)
    } catch (RetrofitError e) {
      if (e.getKind() == RetrofitError.Kind.NETWORK) {
        log.error("Could not find the server ${master.baseUrl}")
        return false
      } else if (e.response.status == 404) {
        return false
      } else if (e.response.status == 401) {
        log.error("Cannot check if $userName is member of ${t.name} teams: Not authorized.", e)
        return false
      }
    }
  }

  @Override
  void afterPropertiesSet() throws Exception {
    Assert.state(gitHubProperties.organization != null, "Supply an organization")
  }

  @Override
  Map<String, Collection<String>> multiLoadRoles(Collection<String> userEmails) {
    if (!userEmails) {
      return [:]
    }
    def emailGroupsMap = [:]
    userEmails.each { String email ->
      emailGroupsMap.put(email, loadRoles(email))
    }
    emailGroupsMap
  }

}
