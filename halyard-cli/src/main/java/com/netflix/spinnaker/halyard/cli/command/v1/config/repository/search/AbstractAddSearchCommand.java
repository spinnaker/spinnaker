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

package com.netflix.spinnaker.halyard.cli.command.v1.config.repository.search;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.Search;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractAddSearchCommand extends AbstractHasSearchCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "add";

  @Parameter(
      variableArity = true,
      names = "--read-permissions",
      description = SearchCommandProperties.READ_PERMISSION_DESCRIPTION)
  private Set<String> readPermissions = new HashSet<>();

  @Parameter(
      variableArity = true,
      names = "--write-permissions",
      description = SearchCommandProperties.WRITE_PERMISSION_DESCRIPTION)
  private Set<String> writePermissions = new HashSet<>();

  protected abstract Search buildSearch(String searchName);

  public String getShortDescription() {
    return "Add a search for the " + getRepositoryName() + " repository service.";
  }

  @Override
  protected void executeThis() {
    String searchName = getSearchName();
    Search search = buildSearch(searchName);
    String repositoryName = getRepositoryName();
    search.getPermissions().add(Authorization.READ, readPermissions);
    search.getPermissions().add(Authorization.WRITE, writePermissions);

    String currentDeployment = getCurrentDeployment();
    new OperationHandler<Void>()
        .setOperation(Daemon.addSearch(currentDeployment, repositoryName, !noValidate, search))
        .setSuccessMessage("Added " + searchName + " for " + repositoryName + ".")
        .setFailureMesssage("Failed to add " + searchName + " for " + repositoryName + ".")
        .get();
  }
}
