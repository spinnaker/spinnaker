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

package com.netflix.spinnaker.halyard.cli.command.v1;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.*;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.ProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.webhooks.WebhookCommand;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * This is a top-level command for dealing with your halconfig.
 *
 * Usage is `$ hal config`
 */
@Parameters()
public class ConfigCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "config";

  @Getter(AccessLevel.PUBLIC)
  private String description = "Configure, validate, and view your halconfig.";

  ConfigCommand() {
    registerSubcommand(new DeploymentEnvironmentCommand());
    registerSubcommand(new FeaturesCommand());
    registerSubcommand(new GenerateCommand());
    registerSubcommand(new MetricStoresCommand());
    registerSubcommand(new PersistentStorageCommand());
    registerSubcommand(new ProviderCommand());
    registerSubcommand(new SecurityCommand());
    registerSubcommand(new VersionConfigCommand());
    registerSubcommand(new WebhookCommand());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
