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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.*;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.ArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.CanaryCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.CiCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.ProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.pubsubs.PubsubCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.repository.RepositoryCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.stats.StatsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.webhook.WebhookCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * This is a top-level command for dealing with your halconfig.
 *
 * <p>Usage is `$ hal config`
 */
@Parameters(separators = "=")
public class ConfigCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "config";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Configure, validate, and view your halconfig.";

  @Parameter(
      names = "--set-current-deployment",
      description =
          "If supplied, set the current active deployment to the supplied value, creating it if need-be.")
  private String setCurrentDeployment;

  ConfigCommand() {
    registerSubcommand(new ArtifactProviderCommand());
    registerSubcommand(new CanaryCommand());
    registerSubcommand(new DeploymentEnvironmentCommand());
    registerSubcommand(new EditConfigCommand());
    registerSubcommand(new FeaturesCommand());
    registerSubcommand(new GenerateCommand());
    registerSubcommand(new MetricStoresCommand());
    registerSubcommand(new NotificationCommand());
    registerSubcommand(new PersistentStorageCommand());
    registerSubcommand(new ProviderCommand());
    registerSubcommand(new PubsubCommand());
    registerSubcommand(new SecurityCommand());
    registerSubcommand(new VersionConfigCommand());
    registerSubcommand(new WebhookCommand());
    registerSubcommand(new CiCommand());
    registerSubcommand(new ListCommand());
    registerSubcommand(new RepositoryCommand());
    registerSubcommand(new StatsCommand());
  }

  @Override
  protected void executeThis() {
    if (setCurrentDeployment != null) {
      new OperationHandler<Void>()
          .setOperation(Daemon.setCurrentDeployment(setCurrentDeployment))
          .setSuccessMessage("Updated current deployment to " + setCurrentDeployment)
          .setFailureMesssage("Failed to update current deployment.")
          .get();

    } else {
      new OperationHandler<DeploymentConfiguration>()
          .setOperation(Daemon.getDeploymentConfiguration(getCurrentDeployment(), !noValidate))
          .setFormat(AnsiFormatUtils.Format.YAML)
          .setUserFormatted(true)
          .setSuccessMessage("Configured deployment: ")
          .setFailureMesssage("Failed to deployment configuration.")
          .get();
    }
  }
}
