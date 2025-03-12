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
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

/** Delete a specific PROVIDER account */
@Parameters(separators = "=")
public abstract class AbstractDeleteArtifactAccountCommand
    extends AbstractHasArtifactAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  public String getShortDescription() {
    return "Delete a specific " + getArtifactProviderName() + " artifact account by name.";
  }

  @Override
  protected void executeThis() {
    deleteArtifactAccount(getArtifactAccountName());
  }

  private void deleteArtifactAccount(String accountName) {
    String currentDeployment = getCurrentDeployment();
    String providerName = getArtifactProviderName();
    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to delete artifact account "
                + accountName
                + " for artifact provider "
                + providerName
                + ".")
        .setSuccessMessage(
            "Successfully deleted artifact account "
                + accountName
                + " for artifact provider "
                + providerName
                + ".")
        .setOperation(
            Daemon.deleteArtifactAccount(currentDeployment, providerName, accountName, !noValidate))
        .get();
  }
}
