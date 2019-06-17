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

package com.netflix.spinnaker.halyard.cli.command.v1.versions;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class ListVersionCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "list";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "List the available Spinnaker versions and their changelogs.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      String.join(
          " ",
          "All Spinnaker releases that have been fully validated are listed here.",
          "You can pick one of these releases to deploy using the `hal config version edit`",
          "command. There are unlisted, non-supported releases as well, but we advise against",
          "running them. For more information, contact the developers at https://join.spinnaker.io.");

  @Override
  protected void executeThis() {
    String version =
        new OperationHandler<String>()
            .setOperation(Daemon.getVersion(getCurrentDeployment(), false))
            .setFailureMesssage("Failed to load your version of Spinnaker.")
            .get();

    Versions versions =
        new OperationHandler<Versions>()
            .setOperation(Daemon.getVersions())
            .setFailureMesssage("Failed to load available Spinnaker versions.")
            .setSuccessMessage(
                "You are on version \"" + version + "\", and the following are available:")
            .setFormat(AnsiFormatUtils.Format.STRING)
            .setUserFormatted(true)
            .get();
  }
}
