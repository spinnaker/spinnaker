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

package com.netflix.spinnaker.halyard.cli.command.v1.backup;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class CreateBackupCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "create";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Create a backup of Halyard's state.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      "This will create a tarball of your halconfig directory, being careful to rewrite "
          + "file paths, so when the tarball is expanded by Halyard on another machine it will still be able to reference "
          + "any files you have explicitly linked with your halconfig - e.g. --kubeconfig-file for Kubernetes, or --json-path "
          + "for GCE.";

  @Override
  protected void executeThis() {
    new OperationHandler<String>()
        .setFailureMesssage("Failed to create a backup.")
        .setSuccessMessage("Successfully created a backup at location: ")
        .setOperation(Daemon.createBackup())
        .setFormat(AnsiFormatUtils.Format.STRING)
        .get();
  }
}
