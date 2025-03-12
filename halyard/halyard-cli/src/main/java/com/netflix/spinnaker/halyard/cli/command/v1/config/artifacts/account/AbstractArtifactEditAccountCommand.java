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
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractArtifactEditAccountCommand<T extends ArtifactAccount>
    extends AbstractHasArtifactAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract ArtifactAccount editArtifactAccount(T account);

  public String getShortDescription() {
    return "Edit an artifact account in the " + getArtifactProviderName() + " artifact provider.";
  }

  @Override
  protected void executeThis() {
    String accountName = getArtifactAccountName();
    String providerName = getArtifactProviderName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    ArtifactAccount account =
        new OperationHandler<ArtifactAccount>()
            .setFailureMesssage(
                "Failed to get account " + accountName + " for provider " + providerName + ".")
            .setOperation(
                Daemon.getArtifactAccount(currentDeployment, providerName, accountName, false))
            .get();

    int originaHash = account.hashCode();

    account = editArtifactAccount((T) account);

    if (originaHash == account.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to edit artifact account "
                + accountName
                + " for artifact provider "
                + providerName
                + ".")
        .setSuccessMessage(
            "Successfully edited artifact account "
                + accountName
                + " for artifact provider "
                + providerName
                + ".")
        .setOperation(
            Daemon.setArtifactAccount(
                currentDeployment, providerName, accountName, !noValidate, account))
        .get();
  }
}
