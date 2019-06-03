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
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class GetCanaryAccountCommand extends AbstractHasCanaryAccountCommand {

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "get";

  @Getter(AccessLevel.PUBLIC)
  private final String serviceIntegration;

  public GetCanaryAccountCommand(String serviceIntegration) {
    this.serviceIntegration = serviceIntegration;
  }

  @Override
  public String getShortDescription() {
    return "Get the specified canary account details for the "
        + getServiceIntegration()
        + " service integration.";
  }

  @Override
  protected void executeThis() {
    AnsiUi.success(AnsiFormatUtils.format(getAccount(getAccountName())));
  }

  private AbstractCanaryAccount getAccount(String accountName) {
    String currentDeployment = getCurrentDeployment();
    String serviceIntegration = getServiceIntegration();
    return new OperationHandler<AbstractCanaryAccount>()
        .setFailureMesssage(
            "Failed to get canary account "
                + accountName
                + " for service integration "
                + serviceIntegration
                + ".")
        .setSuccessMessage("Canary account " + accountName + ": ")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .setOperation(
            Daemon.getCanaryAccount(
                currentDeployment, serviceIntegration.toLowerCase(), accountName, false))
        .get();
  }
}
