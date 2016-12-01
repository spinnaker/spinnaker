/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.providers;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.dockerRegistry.DockerRegistryCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.google.GoogleCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.kubernetes.KubernetesCommand;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * This is a top-level command for dealing with your halconfig.
 *
 * Usage is `$ hal config`
 */
@Parameters()
public class ProviderCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "provider";

  @Getter(AccessLevel.PUBLIC)
  private String description = "Configure, validate, and view your providers";

  public ProviderCommand() {
    registerSubcommand(new KubernetesCommand());
    registerSubcommand(new DockerRegistryCommand());
    registerSubcommand(new GoogleCommand());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
