/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.gcb;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master.AbstractHasAccountCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

/** Delete a specific Google Cloud Build account */
@Parameters(separators = "=")
public class GooglecloudBuildDeleteAccountCommand extends AbstractHasAccountCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "delete";

  protected String getCiName() {
    return "gcb";
  }

  public String getShortDescription() {
    return "Delete a Google Cloud Build account.";
  }

  @Override
  protected void executeThis() {
    String accountName = getAccountName();
    String currentDeployment = getCurrentDeployment();
    String ciName = getCiName();
    new OperationHandler<Void>()
        .setOperation(Daemon.deleteMaster(currentDeployment, ciName, accountName, !noValidate))
        .setSuccessMessage(String.format("Deleted Google Cloud Build account %s.", accountName))
        .setFailureMesssage(
            String.format("Failed to delete Google Cloud Build account %s.", accountName))
        .get();
    AnsiUi.success("Deleted " + accountName);
  }
}
