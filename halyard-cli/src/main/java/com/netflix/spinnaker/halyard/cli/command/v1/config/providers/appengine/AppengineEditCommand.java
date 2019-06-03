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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.appengine;

import static com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils.Format.STRING;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractProviderCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineProvider;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class AppengineEditCommand extends AbstractProviderCommand {

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit Spinnaker's app engine configuration.";

  @Parameter(
      names = "--gcloudPath",
      description = "The path to the gcloud executable on the machine running clouddriver.")
  private String gcloudPath;

  protected String getProviderName() {
    return "appengine";
  }

  public AppengineEditCommand() {}

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    String providerName = getProviderName();
    new OperationHandler<Provider>()
        .setFailureMesssage("Failed to get provider " + providerName + ".")
        .setSuccessMessage("Successfully got provider " + providerName + ".")
        .setFormat(STRING)
        .setUserFormatted(true)
        .setOperation(Daemon.getProvider(currentDeployment, providerName, !noValidate))
        .get();

    AppengineProvider provider =
        (AppengineProvider)
            new OperationHandler<Provider>()
                .setOperation(Daemon.getProvider(currentDeployment, providerName, !noValidate))
                .setFailureMesssage("Failed to get provider " + providerName + ".")
                .get();

    int originalHash = provider.hashCode();

    if (isSet(gcloudPath)) {
      provider.setGcloudPath(gcloudPath);
    }

    if (originalHash == provider.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit update appengine provider.")
        .setSuccessMessage("Successfully edited appengine provider.")
        .setOperation(
            Daemon.setProvider(currentDeployment, getProviderName(), !noValidate, provider))
        .get();
  }
}
