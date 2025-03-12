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
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.AbstractArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactProvider;
import java.util.List;
import lombok.Getter;

@Parameters(separators = "=")
abstract class AbstractListArtifactAccountsCommand extends AbstractArtifactProviderCommand {
  public String getShortDescription() {
    return "List the artifact account names for the "
        + getArtifactProviderName()
        + " artifact provider.";
  }

  @Getter private String commandName = "list";

  private ArtifactProvider getArtifactProvider() {
    String currentDeployment = getCurrentDeployment();
    String providerName = getArtifactProviderName();
    return new OperationHandler<ArtifactProvider>()
        .setFailureMesssage("Failed to get artifact provider " + providerName + ".")
        .setOperation(Daemon.getArtifactProvider(currentDeployment, providerName, !noValidate))
        .get();
  }

  @Override
  protected void executeThis() {
    ArtifactProvider provider = getArtifactProvider();
    List<ArtifactAccount> accounts = provider.getAccounts();
    if (accounts.isEmpty()) {
      AnsiUi.success("No configured artifact accounts for " + getArtifactProviderName() + ".");
    } else {
      AnsiUi.success("Artifact accounts for " + getArtifactProviderName() + ":");
      accounts.forEach(account -> AnsiUi.listItem(account.getName()));
    }
  }
}
