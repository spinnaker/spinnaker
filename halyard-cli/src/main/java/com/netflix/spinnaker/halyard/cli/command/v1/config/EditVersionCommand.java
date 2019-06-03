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

package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditVersionCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Set the desired Spinnaker version.";

  @Parameter(
      names = "--version",
      required = true,
      description =
          "Must be either a version number \"X.Y.Z\" for a specific release of Spinnaker, "
              + "or \"$BRANCH-latest-unvalidated\" for the most recently built (unvalidated) Spinnaker on $BRANCH.")
  private String version;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    new OperationHandler<Void>()
        .setOperation(Daemon.setVersion(currentDeployment, !noValidate, version))
        .setSuccessMessage(
            "Spinnaker has been configured to update/install version \""
                + version
                + "\". "
                + "Deploy this version of Spinnaker with `hal deploy apply`.")
        .setFailureMesssage("Failed to update version.")
        .get();
  }
}
