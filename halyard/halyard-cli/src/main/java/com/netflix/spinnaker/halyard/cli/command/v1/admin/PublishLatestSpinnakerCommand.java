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
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class PublishLatestSpinnakerCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "latest-spinnaker";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Publish the latest version of Spinnaker to the global versions.yml tracking file.";

  @Override
  public String getMainParameter() {
    return "version";
  }

  @Parameter(description = "The latest version of Spinnaker to record.")
  String version;

  public String getVersion() {
    if (version == null) {
      throw new IllegalArgumentException("No version supplied.");
    }
    return version;
  }

  @Override
  protected void executeThis() {
    new OperationHandler<Void>()
        .setFailureMesssage("Failed to publish your version.")
        .setSuccessMessage("Successfully published your version.")
        .setOperation(Daemon.publishLatestSpinnaker(getVersion()))
        .get();
  }
}
