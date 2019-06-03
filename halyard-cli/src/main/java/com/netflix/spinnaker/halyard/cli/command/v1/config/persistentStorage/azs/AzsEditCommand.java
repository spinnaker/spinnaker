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

package com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.azs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.AbstractPersistentStoreEditCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.AzsPersistentStore;

@Parameters(separators = "=")
public class AzsEditCommand extends AbstractPersistentStoreEditCommand<AzsPersistentStore> {
  protected String getPersistentStoreType() {
    return PersistentStore.PersistentStoreType.AZS.getId();
  }

  @Parameter(
      names = "--storage-account-name",
      description = "The name of an Azure Storage Account used for Spinnaker's persistent data.")
  private String storageAccountName;

  @Parameter(
      names = "--storage-account-key",
      description =
          "The key to access the Azure Storage Account used for Spinnaker's persistent data.")
  private String storageAccountKey;

  @Parameter(
      names = "--storage-container-name",
      description =
          "The container name in the chosen storage account to place all of Spinnaker's persistent data.")
  private String storageContainerName = "spinnaker";

  @Override
  protected AzsPersistentStore editPersistentStore(AzsPersistentStore persistentStore) {
    persistentStore.setStorageAccountName(
        isSet(storageAccountName) ? storageAccountName : persistentStore.getStorageAccountName());
    persistentStore.setStorageAccountKey(
        isSet(storageAccountKey) ? storageAccountKey : persistentStore.getStorageAccountKey());
    persistentStore.setStorageContainerName(
        isSet(storageContainerName)
            ? storageContainerName
            : persistentStore.getStorageContainerName());

    return persistentStore;
  }
}
