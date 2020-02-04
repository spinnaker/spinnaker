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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.appengine.AppengineCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.aws.AwsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.azure.AzureCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.cloudfoundry.CloudFoundryCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dcos.DCOSCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dockerRegistry.DockerRegistryCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.ecs.EcsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google.GoogleCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.huaweicloud.HuaweiCloudCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.kubernetes.KubernetesCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.tencentcloud.TencentCloudCommand;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * This is a top-level command for dealing with your halconfig.
 *
 * <p>Usage is `$ hal config provider`
 */
@Parameters(separators = "=")
public class ProviderCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "provider";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Configure, validate, and view the specified provider.";

  public ProviderCommand() {
    registerSubcommand(new AppengineCommand());
    registerSubcommand(new AwsCommand());
    registerSubcommand(new AzureCommand());
    registerSubcommand(new CloudFoundryCommand());
    registerSubcommand(new DCOSCommand());
    registerSubcommand(new DockerRegistryCommand());
    registerSubcommand(new EcsCommand());
    registerSubcommand(new GoogleCommand());
    registerSubcommand(new HuaweiCloudCommand());
    registerSubcommand(new KubernetesCommand());
    registerSubcommand(
        new com.netflix.spinnaker.halyard.cli.command.v1.config.providers.oracle.OracleCommand());
    registerSubcommand(new TencentCloudCommand());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
