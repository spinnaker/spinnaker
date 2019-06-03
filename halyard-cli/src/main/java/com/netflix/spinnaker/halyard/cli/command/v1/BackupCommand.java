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

package com.netflix.spinnaker.halyard.cli.command.v1;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.backup.CreateBackupCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.backup.RestoreBackupCommand;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class BackupCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "backup";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Backup and restore (remote or local) copies of your halconfig and all required files.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      String.join(
          " ",
          "This is used to periodically checkpoint your configured Spinnaker installation as well as",
          "allow you to store all aspects of your configured Spinnaker installation, to be picked up by an installation of Halyard on another machine.");

  public BackupCommand() {
    registerSubcommand(new CreateBackupCommand());
    registerSubcommand(new RestoreBackupCommand());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
