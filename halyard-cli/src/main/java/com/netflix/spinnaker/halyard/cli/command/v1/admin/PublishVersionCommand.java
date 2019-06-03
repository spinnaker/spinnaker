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

package com.netflix.spinnaker.halyard.cli.command.v1.admin;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import java.util.Date;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class PublishVersionCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "version";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Publish a version of Spinnaker to the global versions.yml tracking file.";

  @Parameter(
      names = "--version",
      required = true,
      description = "The version (x.y.z) of Spinnaker to be recorded. This must exist as a BOM.")
  private String version;

  @Parameter(
      names = "--alias",
      required = true,
      description = "The alias this version of Spinnaker goes by.")
  private String alias;

  @Parameter(
      names = "--changelog",
      required = true,
      description = "A link to this Spinnaker release's changelog.")
  private String changelog;

  @Parameter(
      names = "--minimum-halyard-version",
      required = true,
      description = "Minimum version of halyard required to install this release")
  private String minimumHalyardVersion;

  @Override
  protected void executeThis() {
    Versions.Version publishedVersion =
        new Versions.Version()
            .setVersion(version)
            .setAlias(alias)
            .setChangelog(changelog)
            .setMinimumHalyardVersion(minimumHalyardVersion)
            .setLastUpdate(new Date());

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to publish your version.")
        .setSuccessMessage("Successfully published your version.")
        .setOperation(Daemon.publishVersion(publishedVersion))
        .get();
  }
}
