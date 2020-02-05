/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.account.AbstractHasAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.account.DeleteAccountCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.account.ListAccountsCommandBuilder;

@Parameters(separators = "=")
public abstract class AbstractAccountCommand extends AbstractHasAccountCommand {
  @Override
  public String getCommandName() {
    return "account";
  }

  @Override
  public String getShortDescription() {
    return "Manage and view Spinnaker configuration for the "
        + getCiFullName()
        + " service account.";
  }

  protected AbstractAccountCommand() {
    registerSubcommand(
        new DeleteAccountCommandBuilder()
            .setCiName(getCiName())
            .setCiFullName(getCiFullName())
            .build());

    registerSubcommand(
        new ListAccountsCommandBuilder()
            .setCiName(getCiName())
            .setCiFullName(getCiFullName())
            .build());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
