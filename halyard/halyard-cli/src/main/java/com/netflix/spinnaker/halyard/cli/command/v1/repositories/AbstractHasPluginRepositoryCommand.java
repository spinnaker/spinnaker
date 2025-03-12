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
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginRepository;

/** An abstract definition for commands that accept REPOSITORY as a main parameter */
@Parameters(separators = "=")
public abstract class AbstractHasPluginRepositoryCommand extends AbstractConfigCommand {
  @Parameter(description = "The name of the plugin repository to operate on.")
  private String repository;

  @Override
  public String getMainParameter() {
    return "repository";
  }

  public PluginRepository getRepository() {
    return new OperationHandler<PluginRepository>()
        .setFailureMesssage("Failed to get repository")
        .setOperation(
            Daemon.getPluginRepository(getCurrentDeployment(), getPluginRepositoryId(), false))
        .get();
  }

  public String getPluginRepositoryId() {
    if (repository == null) {
      throw new IllegalArgumentException("No plugin repository supplied");
    }
    return repository;
  }
}
