/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.providers.dockerRegistry;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.AbstractProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.dockerRegistry.DisableDockerRegistryCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.dockerRegistry.EnableDockerRegistryCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.dockerRegistry.GetDockerRegistryAccountCommand;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Interact with the dockerRegistry provider
 */
@Parameters()
public class DockerRegistryCommand extends AbstractProviderCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PROTECTED)
  private String providerName = "dockerRegistry";

  public DockerRegistryCommand() {
    GetDockerRegistryAccountCommand getDockerRegistryAccountCommand = new GetDockerRegistryAccountCommand();
    EnableDockerRegistryCommand enableDockerRegistryCommand = new EnableDockerRegistryCommand();
    DisableDockerRegistryCommand disableDockerRegistryCommand = new DisableDockerRegistryCommand();

    this.subcommands.put(getDockerRegistryAccountCommand.getCommandName(), getDockerRegistryAccountCommand);
    this.subcommands.put(enableDockerRegistryCommand.getCommandName(), enableDockerRegistryCommand);
    this.subcommands.put(disableDockerRegistryCommand.getCommandName(), disableDockerRegistryCommand);
  }
}
