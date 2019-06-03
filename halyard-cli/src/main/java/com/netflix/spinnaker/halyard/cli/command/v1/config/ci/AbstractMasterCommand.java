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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master.AbstractHasMasterCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master.DeleteMasterCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master.GetMasterCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.master.ListMastersCommandBuilder;

@Parameters(separators = "=")
public abstract class AbstractMasterCommand extends AbstractHasMasterCommand {
  @Override
  public String getCommandName() {
    return "master";
  }

  @Override
  public String getShortDescription() {
    return "Manage and view Spinnaker configuration for the "
        + getCiName()
        + " Continuous Integration services's master";
  }

  protected AbstractMasterCommand() {
    registerSubcommand(new DeleteMasterCommandBuilder().setCiName(getCiName()).build());

    registerSubcommand(new GetMasterCommandBuilder().setCiName(getCiName()).build());

    registerSubcommand(new ListMastersCommandBuilder().setCiName(getCiName()).build());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
