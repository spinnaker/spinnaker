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
import lombok.AccessLevel;
import lombok.Getter;

/** Delete a specific SERVICE_INTEGRATION canary account */
@Parameters(separators = "=")
public class DeleteCanaryAccountCommand extends AbstractHasCanaryAccountCommand {

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  @Getter(AccessLevel.PUBLIC)
  private final String serviceIntegration;

  public DeleteCanaryAccountCommand(String serviceIntegration) {
    this.serviceIntegration = serviceIntegration;
  }

  @Override
  public String getShortDescription() {
    return "Delete a specific " + getServiceIntegration() + " canary account by name.";
  }

  @Override
  protected void executeThis() {
    deleteAccount(getAccountName());
  }

  private void deleteAccount(String accountName) {
    String currentDeployment = getCurrentDeployment();
    String serviceIntegration = getServiceIntegration();
    new OperationHandler<Void>()
        .setFailureMesssage(
            "Failed to delete canary account "
                + accountName
                + " for service integration "
                + serviceIntegration
                + ".")
        .setSuccessMessage(
            "Successfully deleted canary account "
                + accountName
                + " for service integration "
                + serviceIntegration
                + ".")
        .setOperation(
            Daemon.deleteCanaryAccount(
                currentDeployment, serviceIntegration.toLowerCase(), accountName, !noValidate))
        .get();
  }
}
