/*
 * Copyright 2020 Adevinta.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Parameters(separators = "=")
@EqualsAndHashCode(callSuper = false)
public abstract class AbstractEditFeaturesProviderCommand<A extends Account, P extends Provider<A>>
    extends AbstractProviderCommand {
  String commandName = "edit";

  @Override
  protected void executeThis() {
    String providerName = getProviderName();
    String currentDeployment = getCurrentDeployment();

    Provider provider =
        new OperationHandler<Provider>()
            .setFailureMesssage("Failed to get provider " + providerName + ".")
            .setOperation(Daemon.getProvider(currentDeployment, providerName, false))
            .get();

    int originalHash = provider.hashCode();

    provider = editProvider((P) provider);

    if (originalHash == provider.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit provider " + providerName + ".")
        .setSuccessMessage("Successfully edited provider " + providerName + ".")
        .setOperation(Daemon.setProvider(currentDeployment, providerName, !noValidate, provider))
        .get();
  }

  protected abstract Provider editProvider(P provider);
}
