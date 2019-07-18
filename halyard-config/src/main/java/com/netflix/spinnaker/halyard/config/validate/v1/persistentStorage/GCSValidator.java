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

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.GcsStorageService;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.GcsPersistentStore;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class GCSValidator extends Validator<GcsPersistentStore> {
  @Autowired private AccountService accountService;

  @Autowired private Registry registry;

  @Autowired TaskScheduler taskScheduler;

  private int connectTimeoutSec = 45;
  private int readTimeoutSec = 45;
  private long maxWaitInterval = 60000;
  private long retryIntervalbase = 2;
  private long jitterMultiplier = 1000;
  private long maxRetries = 10;

  @Override
  public void validate(ConfigProblemSetBuilder ps, GcsPersistentStore n) {
    String jsonPath = n.getJsonPath();
    try {
      StorageService storageService =
          new GcsStorageService(
              n.getBucket(),
              n.getBucketLocation(),
              n.getRootFolder(),
              n.getProject(),
              jsonPath != null ? secretSessionManager.decryptAsFile(jsonPath) : "",
              "halyard",
              connectTimeoutSec,
              readTimeoutSec,
              maxWaitInterval,
              retryIntervalbase,
              jitterMultiplier,
              maxRetries,
              taskScheduler,
              registry);

      storageService.ensureBucketExists();
    } catch (Exception e) {
      ps.addProblem(
          Severity.ERROR,
          "Failed to ensure the required bucket \""
              + n.getBucket()
              + "\" exists: "
              + e.getMessage());
    }
  }
}
