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
import com.netflix.spinnaker.halyard.cli.command.v1.config.repository.AbstractRepositoryCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Repository;
import com.netflix.spinnaker.halyard.config.model.v1.node.Search;
import java.util.List;
import lombok.Getter;

@Parameters(separators = "=")
abstract class AbstractListSearchesCommand extends AbstractRepositoryCommand {
  public String getShortDescription() {
    return "List the search names for " + getRepositoryName() + ".";
  }

  @Getter private String commandName = "list";

  private Repository getRepository() {
    String currentDeployment = getCurrentDeployment();
    String repositoryName = getRepositoryName();
    return new OperationHandler<Repository>()
        .setOperation(Daemon.getRepository(currentDeployment, repositoryName, !noValidate))
        .setFailureMesssage("Failed to get " + repositoryName + " wehbook.")
        .get();
  }

  @Override
  protected void executeThis() {
    Repository repository = getRepository();
    List<Search> searches = repository.getSearches();
    if (searches.isEmpty()) {
      AnsiUi.success("No configured searches for " + getRepositoryName() + ".");
    } else {
      AnsiUi.success("Searches for " + getRepositoryName() + ":");
      searches.forEach(search -> AnsiUi.listItem(search.getName()));
    }
  }
}
