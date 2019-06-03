/*
 * Copyright 2017 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.AbstractHasArtifactAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.DeleteArtifactAccountCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.GetArtifactAccountCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.ListArtifactAccountsCommandBuilder;

@Parameters(separators = "=")
public abstract class AbstractArtifactAccountCommand extends AbstractHasArtifactAccountCommand {
  @Override
  public String getCommandName() {
    return "account";
  }

  @Override
  public String getShortDescription() {
    return "Manage and view Spinnaker configuration for the "
        + getArtifactProviderName()
        + " artifact provider's account";
  }

  protected AbstractArtifactAccountCommand() {
    registerSubcommand(
        new DeleteArtifactAccountCommandBuilder()
            .setArtifactProviderName(getArtifactProviderName())
            .build());

    registerSubcommand(
        new GetArtifactAccountCommandBuilder()
            .setArtifactProviderName(getArtifactProviderName())
            .build());

    registerSubcommand(
        new ListArtifactAccountsCommandBuilder()
            .setArtifactProviderName(getArtifactProviderName())
            .build());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
