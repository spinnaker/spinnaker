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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginRepository;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditPluginRepositoryCommand extends AbstractHasPluginRepositoryCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit a plugin repository";

  @Parameter(names = "--url", description = "The location of the plugin repository.")
  private String url;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    PluginRepository pluginRepository = getRepository();

    pluginRepository.setUrl(isSet(url) ? url : pluginRepository.getUrl());

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit plugin repository " + pluginRepository.getId() + ".")
        .setSuccessMessage(
            "Successfully edited plugin repository " + pluginRepository.getId() + ".")
        .setOperation(
            Daemon.setPluginRepository(
                currentDeployment, pluginRepository.getId(), !noValidate, pluginRepository))
        .get();
  }
}
