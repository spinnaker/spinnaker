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

package com.netflix.spinnaker.halyard.cli.command.v1.config.pubsubs;

import static com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils.Format.STRING;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsub;

@Parameters(separators = "=")
public abstract class AbstractNamedPubsubCommand extends AbstractPubsubCommand {
  @Override
  public String getCommandName() {
    return getPubsubName();
  }

  @Override
  public String getShortDescription() {
    return "Manage and view Spinnaker configuration for the " + getPubsubName() + " pubsub";
  }

  protected AbstractNamedPubsubCommand() {
    registerSubcommand(
        new PubsubEnableDisableCommandBuilder()
            .setPubsubName(getPubsubName())
            .setEnable(false)
            .build());

    registerSubcommand(
        new PubsubEnableDisableCommandBuilder()
            .setPubsubName(getPubsubName())
            .setEnable(true)
            .build());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String pubsubName = getPubsubName();
    new OperationHandler<Pubsub>()
        .setFailureMesssage("Failed to get pubsub " + pubsubName + ".")
        .setSuccessMessage("Successfully got pubsub " + pubsubName + ".")
        .setFormat(STRING)
        .setUserFormatted(true)
        .setOperation(Daemon.getPubsub(currentDeployment, pubsubName, !noValidate))
        .get();
  }
}
