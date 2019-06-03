/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryServiceIntegration;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class ListCanaryAccountsCommand extends AbstractCanaryServiceIntegrationCommand {

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "list";

  @Getter(AccessLevel.PUBLIC)
  private final String serviceIntegration;

  public ListCanaryAccountsCommand(String serviceIntegration) {
    this.serviceIntegration = serviceIntegration;
  }

  @Override
  public String getShortDescription() {
    return "List the canary account names for the "
        + getServiceIntegration()
        + " service integration.";
  }

  @Override
  protected void executeThis() {
    AbstractCanaryServiceIntegration serviceIntegration =
        CanaryUtils.getServiceIntegrationByName(
            null, getCurrentDeployment(), getServiceIntegration(), noValidate);
    List<AbstractCanaryAccount> accounts = serviceIntegration.getAccounts();

    if (accounts.isEmpty()) {
      AnsiUi.success("No configured accounts for " + getServiceIntegration() + ".");
    } else {
      AnsiUi.success("Accounts for " + getServiceIntegration() + ":");
      accounts.forEach(account -> AnsiUi.listItem(account.getName()));
    }
  }
}
