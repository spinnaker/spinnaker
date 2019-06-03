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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractProviderCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractEditBakeryDefaultsCommand<T extends BakeryDefaults>
    extends AbstractProviderCommand {
  @Getter(AccessLevel.PROTECTED)
  private Map<String, NestableCommand> subcommands = new HashMap<>();

  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  protected abstract BakeryDefaults editBakeryDefaults(T BakeryDefaults);

  public String getShortDescription() {
    return "Edit the " + getProviderName() + " provider's bakery default options.";
  }

  @Override
  protected void executeThis() {
    String providerName = getProviderName();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    BakeryDefaults defaults =
        new OperationHandler<BakeryDefaults>()
            .setFailureMesssage("Failed to get bakery defaults for " + providerName + "'s bakery.")
            .setOperation(Daemon.getBakeryDefaults(currentDeployment, providerName, false))
            .get();

    int originalHash = defaults.hashCode();

    defaults = editBakeryDefaults((T) defaults);

    if (originalHash == defaults.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setSuccessMessage("Successfully edited bakery defaults for " + providerName + "'s bakery.")
        .setFailureMesssage("Failed to edit bakery defaults for " + providerName + "'s bakery.")
        .setOperation(
            Daemon.setBakeryDefaults(currentDeployment, providerName, !noValidate, defaults))
        .get();
  }
}
