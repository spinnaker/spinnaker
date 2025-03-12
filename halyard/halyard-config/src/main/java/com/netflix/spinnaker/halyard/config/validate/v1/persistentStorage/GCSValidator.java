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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.Credentials;
import com.google.cloud.storage.Storage;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.config.GcsProperties;
import com.netflix.spinnaker.front50.model.GcsStorageService;
import com.netflix.spinnaker.halyard.config.config.v1.GCSConfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.GcsPersistentStore;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
  private int maxWaitInterval = 60000;
  private int retryIntervalbase = 2;
  private int jitterMultiplier = 1000;
  private int maxRetries = 10;

  @Override
  public void validate(ConfigProblemSetBuilder ps, GcsPersistentStore n) {
    GcsProperties gcsProperties = getGoogleCloudStorageProperties(n);
    try {
      Credentials credentials = GCSConfig.getGcsCredentials(gcsProperties);
      Storage googleCloudStorage = GCSConfig.getGoogleCloudStorage(credentials, gcsProperties);
      ExecutorService executor =
          Executors.newCachedThreadPool(
              new ThreadFactoryBuilder()
                  .setNameFormat(GcsStorageService.class.getName() + "-%s")
                  .build());
      GcsStorageService storageService =
          new GcsStorageService(
              googleCloudStorage,
              n.getBucket(),
              n.getBucketLocation(),
              n.getRootFolder(),
              n.getProject(),
              new ObjectMapper(),
              executor);

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

  public GcsProperties getGoogleCloudStorageProperties(GcsPersistentStore n) {
    GcsProperties gcsProperties = new GcsProperties();
    Path jsonPath = validatingFileDecryptPath(n.getJsonPath());
    gcsProperties.setJsonPath(jsonPath.toString());
    gcsProperties.setProject(n.getProject());
    gcsProperties.setBucket(n.getBucket());
    gcsProperties.setBucketLocation(n.getBucketLocation());
    return gcsProperties;
  }
}
