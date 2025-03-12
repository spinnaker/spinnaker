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

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractAddArtifactAccountCommand extends AbstractHasArtifactAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "add";

  protected abstract ArtifactAccount buildArtifactAccount(String accountName);

  protected abstract ArtifactAccount emptyArtifactAccount();

  public String getShortDescription() {
    return "Add an artifact account to the " + getArtifactProviderName() + " artifact provider.";
  }

  @Override
  protected void executeThis() {
    String accountName = getArtifactAccountName();
    ArtifactAccount account = buildArtifactAccount(accountName);
    String providerName = getArtifactProviderName();

    String currentDeployment = getCurrentDeployment();
    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to add artifact account "
                + accountName
                + " for artifact provider "
                + providerName
                + ".")
        .setSuccessMessage(
            "Successfully added artifact account "
                + accountName
                + " for artifact provider "
                + providerName
                + ".")
        .setOperation(
            Daemon.addArtifactAccount(currentDeployment, providerName, !noValidate, account))
        .get();
  }
}
