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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.admin;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class DeprecateVersionCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "version";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Deprecate a version of Spinnaker, removing it from the global versions.yml tracking file.";

  @Parameter(
      names = "--version",
      required = true,
      description = "The version (x.y.z) of Spinnaker to be deprecated.")
  private String version;

  @Parameter(
      names = "--illegal-reason",
      description =
          "If supplied, the version will not only be deprecated, but will no longer be installable by Halyard for the supplied reason")
  private String illegalReason;

  @Override
  protected void executeThis() {
    new OperationHandler<Void>()
        .setFailureMesssage("Failed to deprecate your version.")
        .setSuccessMessage("Successfully deprecated your version.")
        .setOperation(
            Daemon.deprecateVersion(new Versions.Version().setVersion(version), illegalReason))
        .get();
  }
}
