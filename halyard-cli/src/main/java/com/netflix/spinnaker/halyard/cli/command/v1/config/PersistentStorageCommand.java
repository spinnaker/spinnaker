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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.EditPersistentStorageCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.azs.AzsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.gcs.GcsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.oracle.OracleCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.s3.S3Command;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiFormatUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStorage;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class PersistentStorageCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "storage";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Show Spinnaker's persistent storage configuration.";

  public PersistentStorageCommand() {
    registerSubcommand(new GcsCommand());
    registerSubcommand(new S3Command());
    registerSubcommand(new AzsCommand());
    registerSubcommand(new OracleCommand());
    registerSubcommand(new EditPersistentStorageCommand());
  }

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    new OperationHandler<PersistentStorage>()
        .setOperation(Daemon.getPersistentStorage(currentDeployment, !noValidate))
        .setFailureMesssage("Failed to load persistent storage.")
        .setSuccessMessage("Configured persistent storage: ")
        .setFormat(AnsiFormatUtils.Format.STRING)
        .setUserFormatted(true)
        .get();
  }
}
