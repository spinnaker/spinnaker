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
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractEditAuthnMethodCommand<T extends AuthnMethod>
    extends AbstractAuthnMethodCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract AuthnMethod editAuthnMethod(T authnMethod);

  public String getShortDescription() {
    return "Edit the " + getMethod().id + " authentication method.";
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String authnMethodName = getMethod().id;
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    AuthnMethod authnMethod =
        new OperationHandler<AuthnMethod>()
            .setOperation(Daemon.getAuthnMethod(currentDeployment, authnMethodName, false))
            .setFailureMesssage("Failed to get " + authnMethodName + " method.")
            .get();

    new OperationHandler<Void>()
        .setOperation(
            Daemon.setAuthnMethod(
                currentDeployment, authnMethodName, !noValidate, editAuthnMethod((T) authnMethod)))
        .setFailureMesssage("Failed to edit " + authnMethodName + " method.")
        .setSuccessMessage("Successfully edited " + authnMethodName + " method.")
        .get();
  }
}
