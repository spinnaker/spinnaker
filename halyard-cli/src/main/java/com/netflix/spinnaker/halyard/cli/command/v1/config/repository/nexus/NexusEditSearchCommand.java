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

package com.netflix.spinnaker.halyard.cli.command.v1.config.repository.nexus;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.repository.search.AbstractEditSearchCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Search;
import com.netflix.spinnaker.halyard.config.model.v1.repository.nexus.NexusSearch;

@Parameters(separators = "=")
public class NexusEditSearchCommand extends AbstractEditSearchCommand<NexusSearch> {
  protected String getRepositoryName() {
    return "nexus";
  }

  @Parameter(names = "--username", description = NexusCommandProperties.USERNAME_DESCRIPTION)
  public String username;

  @Parameter(names = "--password", description = NexusCommandProperties.PASSWORD_DESCRIPTION)
  public String password;

  @Parameter(names = "--base-url", description = NexusCommandProperties.BASE_URL_DESCRIPTION)
  private String baseUrl;

  @Parameter(names = "--repo", description = NexusCommandProperties.REPO_DESCRIPTION)
  public String repo;

  @Parameter(names = "--nodeId", description = NexusCommandProperties.NODE_ID_DESCRIPTION)
  public String nodeId;

  @Override
  protected Search editSearch(NexusSearch search) {
    search.setBaseUrl(isSet(baseUrl) ? baseUrl : search.getBaseUrl());
    search.setRepo(isSet(repo) ? repo : search.getRepo());
    search.setNodeId(isSet(nodeId) ? nodeId : search.getNodeId());
    search.setUsername(isSet(username) ? username : search.getUsername());
    search.setPassword(isSet(password) ? password : search.getPassword());
    return search;
  }
}
