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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.ProtectedCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class RestoreBackupCommand extends NestableCommand implements ProtectedCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "restore";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Restore an existing backup.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      "Restore an existing backup. This backup does _not_ necessarily have to come from "
          + "the machine it is being restored on - since all files referenced by your halconfig are included in the halconfig backup. "
          + "As a result of this, keep in mind that after restoring a backup, all your required files are now in $halconfig/.backup/required-files.";

  @Parameter(
      names = "--backup-path",
      converter = LocalFileConverter.class,
      required = true,
      description = "This is the path to the .tar file created by running `hal backup create`.")
  private String backupPath;

  @Override
  protected void executeThis() {
    new OperationHandler<Void>()
        .setFailureMesssage("Failed to restore the backup.")
        .setSuccessMessage("Successfully restored your backup.")
        .setOperation(Daemon.restoreBackup(backupPath))
        .get();
  }

  @Override
  public String getPrompt() {
    return "This will override your entire current halconfig directory.";
  }
}
