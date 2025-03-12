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
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class LatestVersionCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "latest";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Get the latest released, validated version number of Spinnaker.";

  @Override
  protected void executeThis() {
    new OperationHandler<String>()
        .setOperation(Daemon.getLatest())
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .setFailureMesssage("Failed to get the latest version of Spinnaker.")
        .get();
  }
}
