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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Parameters(separators = "=")
@EqualsAndHashCode(callSuper = false)
public abstract class AbstractEditCiCommand<A extends CIAccount, C extends Ci<A>>
    extends AbstractCiCommand {
  String commandName = "edit";

  @Override
  protected void executeThis() {
    String ciName = getCiName();
    String currentDeployment = getCurrentDeployment();

    Ci ci =
        new OperationHandler<Ci>()
            .setFailureMesssage("Failed to get ci provider " + ciName + ".")
            .setOperation(Daemon.getCi(currentDeployment, ciName, false))
            .get();

    int originalHash = ci.hashCode();

    ci = editCi((C) ci);

    if (originalHash == ci.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit ci provider " + ci + ".")
        .setSuccessMessage("Successfully edited ci provider " + ci + ".")
        .setOperation(Daemon.setCi(currentDeployment, ciName, !noValidate, ci))
        .get();
  }

  protected abstract Ci editCi(C ci);
}
