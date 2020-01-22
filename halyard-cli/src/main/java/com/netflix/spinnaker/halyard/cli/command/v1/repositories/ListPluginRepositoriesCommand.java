/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.repositories;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class ListPluginRepositoriesCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "list";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "List all plugin repositories";

  private List<PluginRepository> getPluginRepositories() {
    String currentDeployment = getCurrentDeployment();
    Map<String, PluginRepository> pluginRepositories =
        new OperationHandler<Map<String, PluginRepository>>()
            .setFailureMesssage("Failed to get plugin repositories.")
            .setOperation(Daemon.getPluginRepositories(currentDeployment, !noValidate))
            .get();
    return pluginRepositories.entrySet().stream()
        .map(r -> r.getValue())
        .collect(Collectors.toList());
  }

  @Override
  protected void executeThis() {
    List<PluginRepository> pluginRepositories = getPluginRepositories();
    if (pluginRepositories.isEmpty()) {
      AnsiUi.success("No configured plugin repositories.");
    } else {
      AnsiUi.success("Plugin repositories:");
      pluginRepositories.forEach(repo -> AnsiUi.listItem(repo.getId()));
    }
  }
}
