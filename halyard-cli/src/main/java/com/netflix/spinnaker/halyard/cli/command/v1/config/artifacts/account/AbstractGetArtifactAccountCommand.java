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
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;
import lombok.Getter;

@Parameters(separators = "=")
abstract class AbstractGetArtifactAccountCommand extends AbstractHasArtifactAccountCommand {
  public String getShortDescription() {
    return "Get the specified account details for the " + getArtifactProviderName() + " provider.";
  }

  @Getter private String commandName = "get";

  @Override
  protected void executeThis() {
    getArtifactAccount(getArtifactAccountName());
  }

  private ArtifactAccount getArtifactAccount(String accountName) {
    String currentDeployment = getCurrentDeployment();
    String providerName = getArtifactProviderName();
    return new OperationHandler<ArtifactAccount>()
        .setFailureMesssage(
            "Failed to get artifact account " + accountName + " for provider " + providerName + ".")
        .setSuccessMessage("Artifact account " + accountName + ": ")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .setOperation(
            Daemon.getArtifactAccount(currentDeployment, providerName, accountName, false))
        .get();
  }
}
