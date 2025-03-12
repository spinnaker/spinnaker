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

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public abstract class AbstractPersistentStoreEditCommand<T extends PersistentStore>
    extends AbstractPersistentStoreCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Edit configuration for the \"" + getPersistentStoreType() + "\" persistent store.";

  protected abstract PersistentStore editPersistentStore(T persistentStore);

  @Override
  protected void executeThis() {
    String persistentStoreType = getPersistentStoreType();
    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    PersistentStore persistentStore =
        new OperationHandler<PersistentStore>()
            .setFailureMesssage("Failed to get persistent store \"" + persistentStoreType + "\".")
            .setOperation(Daemon.getPersistentStore(currentDeployment, persistentStoreType, false))
            .get();

    int originalHash = persistentStore.hashCode();

    persistentStore = editPersistentStore((T) persistentStore);

    if (originalHash == persistentStore.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit persistent store \"" + persistentStoreType + "\".")
        .setSuccessMessage("Successfully edited persistent store \"" + persistentStoreType + "\".")
        .setOperation(
            Daemon.setPersistentStore(
                currentDeployment, persistentStoreType, !noValidate, persistentStore))
        .get();
  }
}
