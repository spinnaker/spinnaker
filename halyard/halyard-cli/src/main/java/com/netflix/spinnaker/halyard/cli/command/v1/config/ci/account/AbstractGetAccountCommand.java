/*
 * Copyright 2020 Amazon, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.account;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractGetAccountCommand<T extends CIAccount>
    extends AbstractHasAccountCommand {
  @Getter private String commandName = "get";

  public String getShortDescription() {
    return "Get the account details for " + getCiFullName() + ".";
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String masterName = getAccountName();
    String ciName = getCiName();
    new OperationHandler<CIAccount>()
        .setOperation(Daemon.getMaster(currentDeployment, ciName, masterName, !noValidate))
        .setFailureMesssage(String.format("Failed to get %s account.", getCiFullName()))
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .get();
  }
}
