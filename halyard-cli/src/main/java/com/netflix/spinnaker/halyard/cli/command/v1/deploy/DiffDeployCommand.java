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

package com.netflix.spinnaker.halyard.cli.command.v1.deploy;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeDiff;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class DiffDeployCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "diff";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "This shows what changes you have made since Spinnaker was last deployed.";

  @Override
  protected void executeThis() {
    String deploymentName = getCurrentDeployment();

    NodeDiff result =
        new OperationHandler<NodeDiff>()
            .setFailureMesssage("Failed to generate config diff.")
            .setOperation(Daemon.configDiff(deploymentName, !noValidate))
            .get();

    if (result == null) {
      AnsiUi.raw("No changes have been made to your configuration.");
    } else {
      AnsiUi.raw(AnsiFormatUtils.format(result));
    }
  }
}
