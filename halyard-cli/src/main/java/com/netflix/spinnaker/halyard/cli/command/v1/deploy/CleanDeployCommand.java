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
import com.netflix.spinnaker.halyard.cli.command.v1.ProtectedCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class CleanDeployCommand extends AbstractConfigCommand implements ProtectedCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "clean";

  @Getter(AccessLevel.PUBLIC)
  private String prompt = "This command cannot be undone.";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Remove all Spinnaker artifacts in your target deployment environment.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      "This command destroys all Spinnaker artifacts in your target deployment environment. This cannot be undone, so use with care. This does not delete Halyard nor any of the configuration.";

  @Override
  protected void executeThis() {
    new OperationHandler<Void>()
        .setFailureMesssage("Failed to remove Spinnaker.")
        .setSuccessMessage("Successfully removed Spinnaker.")
        .setOperation(Daemon.cleanDeployment(getCurrentDeployment(), !noValidate))
        .get();
  }
}
