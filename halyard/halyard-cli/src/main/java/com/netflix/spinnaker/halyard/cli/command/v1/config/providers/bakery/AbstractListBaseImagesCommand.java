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
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractProviderCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import java.util.List;
import lombok.Getter;

@Parameters(separators = "=")
abstract class AbstractListBaseImagesCommand extends AbstractProviderCommand {
  public String getShortDescription() {
    return "List the base image names for the " + getProviderName() + " provider.";
  }

  @Getter private String commandName = "list";

  @Override
  protected void executeThis() {
    String providerName = getProviderName();
    String currentDeployment = getCurrentDeployment();

    BakeryDefaults bakeryDefaults =
        new OperationHandler<BakeryDefaults>()
            .setFailureMesssage("Failed to get bakery defaults for " + providerName + "'s bakery.")
            .setOperation(Daemon.getBakeryDefaults(currentDeployment, providerName, !noValidate))
            .get();
    List<BaseImage> baseImages = bakeryDefaults.getBaseImages();

    if (baseImages.isEmpty()) {
      AnsiUi.success("No configured base images for " + getProviderName() + ".");
    } else {
      AnsiUi.success("Base images for " + getProviderName() + ":");
      baseImages.forEach(baseImage -> AnsiUi.listItem(baseImage.getBaseImage().getId()));
    }
  }
}
