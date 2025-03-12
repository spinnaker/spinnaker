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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.AbstractCiCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;
import java.util.List;
import lombok.Getter;

@Parameters(separators = "=")
abstract class AbstractListMastersCommand extends AbstractCiCommand {
  public String getShortDescription() {
    return "List the master names for " + getCiName() + ".";
  }

  @Getter private String commandName = "list";

  private Ci getCi() {
    String currentDeployment = getCurrentDeployment();
    String ciName = getCiName();
    return new OperationHandler<Ci>()
        .setOperation(Daemon.getCi(currentDeployment, ciName, !noValidate))
        .setFailureMesssage("Failed to get " + ciName + " wehbook.")
        .get();
  }

  @Override
  protected void executeThis() {
    Ci ci = getCi();
    List<CIAccount> account = ci.listAccounts();
    if (account.isEmpty()) {
      AnsiUi.success("No configured masters for " + getCiName() + ".");
    } else {
      AnsiUi.success("Masters for " + getCiName() + ":");
      account.forEach(master -> AnsiUi.listItem(master.getName()));
    }
  }
}
