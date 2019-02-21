/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.canary.google;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.front50.model.GcsStorageService;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.halyard.config.config.v1.secrets.SecretSessionManager;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.canary.CanaryAccountValidator;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Data
@EqualsAndHashCode(callSuper = false)
@Component
public class GoogleCanaryAccountValidator extends CanaryAccountValidator {

  @Autowired
  private SecretSessionManager secretSessionManager;

  @Setter
  private String halyardVersion;

  @Setter
  private Registry registry;

  @Setter
  TaskScheduler taskScheduler;

  private int connectTimeoutSec = 45;
  private int readTimeoutSec = 45;
  private long maxWaitInterval = 60000;
  private long retryIntervalBase = 2;
  private long jitterMultiplier = 1000;
  private long maxRetries = 10;

  @Override
  public void validate(ConfigProblemSetBuilder p, AbstractCanaryAccount n) {
    super.validate(p, n);

    GoogleCanaryAccount canaryAccount = (GoogleCanaryAccount)n;

    DaemonTaskHandler.message("Validating " + n.getNodeName() + " with " + GoogleCanaryAccountValidator.class.getSimpleName());

    GoogleNamedAccountCredentials credentials = canaryAccount.getNamedAccountCredentials(halyardVersion, secretSessionManager, p);

    if (credentials == null) {
      return;
    }

    String jsonPath = canaryAccount.getJsonPath();

    try {
      StorageService storageService = new GcsStorageService(
          canaryAccount.getBucket(),
          canaryAccount.getBucketLocation(),
          canaryAccount.getRootFolder(),
          canaryAccount.getProject(),
          jsonPath != null ? secretSessionManager.decryptAsFile(jsonPath) : "",
          "halyard",
          connectTimeoutSec,
          readTimeoutSec,
          maxWaitInterval,
          retryIntervalBase,
          jitterMultiplier,
          maxRetries,
          taskScheduler,
          registry);

      storageService.ensureBucketExists();
    } catch (Exception e) {
      p.addProblem(Severity.ERROR, "Failed to ensure the required bucket \"" + canaryAccount.getBucket() + "\" exists: " + e.getMessage());
    }
  }
}