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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;

@Parameters(separators = "=")
public abstract class AuthnMethodCommand extends AbstractConfigCommand {
  public String getCommandName() {
    return getMethod().id;
  }

  public abstract AuthnMethod.Method getMethod();

  public String getShortDescription() {
    return "Configure the " + getMethod().id + " method for authenticating.";
  }

  protected AuthnMethodCommand() {
    registerSubcommand(
        new AuthnMethodEnableDisableCommandBuilder()
            .setEnable(true)
            .setMethod(getMethod())
            .build());

    registerSubcommand(
        new AuthnMethodEnableDisableCommandBuilder()
            .setEnable(false)
            .setMethod(getMethod())
            .build());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String authnMethodName = getMethod().id;

    new OperationHandler<AuthnMethod>()
        .setOperation(Daemon.getAuthnMethod(currentDeployment, authnMethodName, !noValidate))
        .setFailureMesssage("Failed to get " + authnMethodName + " method.")
        .setSuccessMessage("Configured " + authnMethodName + " method: ")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .get();
  }
}
