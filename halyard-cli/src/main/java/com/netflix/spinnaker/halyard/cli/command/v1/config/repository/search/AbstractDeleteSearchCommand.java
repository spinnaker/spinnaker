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

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

/** Delete a specific PROVIDER search */
@Parameters(separators = "=")
public abstract class AbstractDeleteSearchCommand extends AbstractHasSearchCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  public String getShortDescription() {
    return "Delete a specific " + getRepositoryName() + " search by name.";
  }

  @Override
  protected void executeThis() {
    deleteSearch(getSearchName());
    AnsiUi.success("Deleted " + getSearchName());
  }

  private void deleteSearch(String searchName) {
    String currentDeployment = getCurrentDeployment();
    String repositoryName = getRepositoryName();
    new OperationHandler<Void>()
        .setOperation(
            Daemon.deleteSearch(currentDeployment, repositoryName, searchName, !noValidate))
        .setSuccessMessage("Deleted " + searchName + " for " + repositoryName + ".")
        .setFailureMesssage("Failed to delete " + searchName + " for " + repositoryName + ".")
        .get();
  }
}
