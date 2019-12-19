/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.repository.artifactory;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.repository.search.AbstractEditSearchCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Search;
import com.netflix.spinnaker.halyard.config.model.v1.repository.artifactory.ArtifactorySearch;

@Parameters(separators = "=")
public class ArtifactoryEditSearchCommand extends AbstractEditSearchCommand<ArtifactorySearch> {
  protected String getRepositoryName() {
    return "artifactory";
  }

  @Parameter(names = "--username", description = ArtifactoryCommandProperties.USERNAME_DESCRIPTION)
  public String username;

  @Parameter(names = "--password", description = ArtifactoryCommandProperties.PASSWORD_DESCRIPTION)
  public String password;

  @Parameter(names = "--base-url", description = ArtifactoryCommandProperties.BASE_URL_DESCRIPTION)
  private String baseUrl;

  @Parameter(names = "--repo", description = ArtifactoryCommandProperties.REPO_DESCRIPTION)
  public String repo;

  @Parameter(names = "--groupId", description = ArtifactoryCommandProperties.GROUP_ID_DESCRIPTION)
  public String groupId;

  @Parameter(
      names = "--repo-type",
      description = ArtifactoryCommandProperties.REPO_TYPE_DESCRIPTION)
  public RepositoryType repoType;

  @Override
  protected Search editSearch(ArtifactorySearch search) {
    search.setBaseUrl(defaultIfNull(baseUrl, search.getBaseUrl()));
    search.setRepo(defaultIfNull(repo, search.getRepo()));
    search.setGroupId(defaultIfNull(groupId, search.getGroupId()));
    search.setRepoType(isSet(repoType) ? repoType.getType() : search.getRepoType());
    search.setUsername(defaultIfNull(username, search.getUsername()));
    search.setPassword(defaultIfNull(password, search.getPassword()));
    return search;
  }
}
