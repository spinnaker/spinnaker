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
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginRepository;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class DeletePluginRepositoryCommand extends AbstractHasPluginRepositoryCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Delete a plugin repository";

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    PluginRepository pluginRepository = getRepository();
    String id = pluginRepository.getId();

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to delete plugin repository " + id + ".")
        .setSuccessMessage("Successfully deleted plugin repository " + id + ".")
        .setOperation(Daemon.deletePluginRepository(currentDeployment, id, !noValidate))
        .get();
  }
}
