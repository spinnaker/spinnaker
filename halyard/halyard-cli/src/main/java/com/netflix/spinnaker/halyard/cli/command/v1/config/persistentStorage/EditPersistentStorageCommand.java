/*
 * Copyright 2017 Microsoft, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.PersistentStoreTypeConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStorage;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditPersistentStorageCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit Spinnaker's persistent storage.";

  @Parameter(
      names = "--type",
      required = true,
      converter = PersistentStoreTypeConverter.class,
      description = "The type of the persistent store to use for Spinnaker.")
  private PersistentStore.PersistentStoreType type;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    PersistentStorage persistentStorage =
        new OperationHandler<PersistentStorage>()
            .setFailureMesssage("Failed to get persistent storage.")
            .setOperation(Daemon.getPersistentStorage(currentDeployment, false))
            .get();

    int originalHash = persistentStorage.hashCode();

    persistentStorage.setPersistentStoreType(
        isSet(type) ? type : persistentStorage.getPersistentStoreType());

    if (originalHash == persistentStorage.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(
            Daemon.setPersistentStorage(currentDeployment, !noValidate, persistentStorage))
        .setFailureMesssage("Failed to edit persistent storage.")
        .setSuccessMessage("Successfully edited persistent storage.")
        .get();
  }
}
