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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractProviderCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import java.util.List;
import lombok.Getter;

@Parameters(separators = "=")
abstract class AbstractListAccountsCommand extends AbstractProviderCommand {
  public String getShortDescription() {
    return "List the account names for the " + getProviderName() + " provider.";
  }

  @Getter private String commandName = "list";

  private Provider getProvider() {
    String currentDeployment = getCurrentDeployment();
    String providerName = getProviderName();
    return new OperationHandler<Provider>()
        .setFailureMesssage("Failed to get provider " + providerName + ".")
        .setOperation(Daemon.getProvider(currentDeployment, providerName, !noValidate))
        .get();
  }

  @Override
  protected void executeThis() {
    Provider provider = getProvider();
    List<Account> accounts = provider.getAccounts();
    if (accounts.isEmpty()) {
      AnsiUi.success("No configured accounts for " + getProviderName() + ".");
    } else {
      AnsiUi.success("Accounts for " + getProviderName() + ":");
      accounts.forEach(account -> AnsiUi.listItem(account.getName()));
    }
  }
}
