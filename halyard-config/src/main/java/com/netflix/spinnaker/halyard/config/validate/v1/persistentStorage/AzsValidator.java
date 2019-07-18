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

package com.netflix.spinnaker.halyard.config.validate.v1.persistentStorage;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.AzsPersistentStore;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.springframework.stereotype.Component;

@Component
public class AzsValidator extends Validator<AzsPersistentStore> {
  @Override
  public void validate(ConfigProblemSetBuilder ps, AzsPersistentStore n) {
    String connectionString =
        "DefaultEndpointsProtocol=https;AccountName="
            + n.getStorageAccountName()
            + ";AccountKey="
            + secretSessionManager.decrypt(n.getStorageAccountKey());

    try {
      CloudStorageAccount storageAccount = CloudStorageAccount.parse(connectionString);

      CloudBlobContainer container =
          storageAccount.createCloudBlobClient().getContainerReference(n.getStorageContainerName());
      container.exists();
    } catch (Exception e) {
      ps.addProblem(
          Problem.Severity.ERROR,
          "Failed to connect to the Azure storage account \""
              + n.getStorageAccountName()
              + "\": "
              + e.getMessage());
      return;
    }
  }
}
