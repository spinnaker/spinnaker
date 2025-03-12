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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.Credentials;
import com.google.cloud.storage.Storage;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.front50.config.GcsProperties;
import com.netflix.spinnaker.front50.model.GcsStorageService;
import com.netflix.spinnaker.halyard.config.config.v1.GCSConfig;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.canary.CanaryAccountValidator;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.TaskScheduler;

@Data
@EqualsAndHashCode(callSuper = false)
public class GoogleCanaryAccountValidator extends CanaryAccountValidator<GoogleCanaryAccount> {

  private String halyardVersion;

  private Registry registry;

  TaskScheduler taskScheduler;

  private int connectTimeoutSec = 45;
  private int readTimeoutSec = 45;
  private int maxWaitInterval = 60000;
  private int retryIntervalBase = 2;
  private int jitterMultiplier = 1000;
  private int maxRetries = 10;

  GoogleCanaryAccountValidator(SecretSessionManager secretSessionManager) {
    this.secretSessionManager = secretSessionManager;
  }

  @Override
  public void validate(ConfigProblemSetBuilder p, GoogleCanaryAccount n) {
    super.validate(p, n);

    DaemonTaskHandler.message(
        "Validating "
            + n.getNodeName()
            + " with "
            + GoogleCanaryAccountValidator.class.getSimpleName());

    GoogleNamedAccountCredentials credentials = getNamedAccountCredentials(p, n);

    if (credentials == null) {
      return;
    }

    GcsProperties gcsProperties = getGoogleCloudStorageProperties(n);

    try {
      Credentials gcsCredentials = GCSConfig.getGcsCredentials(gcsProperties);
      Storage googleCloudStorage = GCSConfig.getGoogleCloudStorage(gcsCredentials, gcsProperties);
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
      p.addProblem(
          Severity.ERROR,
          "Failed to ensure the required bucket \""
              + n.getBucket()
              + "\" exists: "
              + e.getMessage());
    }
  }

  private GoogleNamedAccountCredentials getNamedAccountCredentials(
      ConfigProblemSetBuilder p, GoogleCanaryAccount canaryAccount) {
    String jsonKey = null;
    if (!StringUtils.isEmpty(canaryAccount.getJsonPath())) {
      jsonKey = validatingFileDecrypt(p, canaryAccount.getJsonPath());

      if (jsonKey == null) {
        return null;
      } else if (jsonKey.isEmpty()) {
        p.addProblem(Problem.Severity.WARNING, "The supplied credentials file is empty.");
      }
    }

    if (StringUtils.isEmpty(canaryAccount.getProject())) {
      p.addProblem(Problem.Severity.ERROR, "No google project supplied.");
      return null;
    }

    try {
      return new GoogleNamedAccountCredentials.Builder()
          .name(canaryAccount.getName())
          .jsonKey(jsonKey)
          .project(canaryAccount.getProject())
          .applicationName("halyard " + halyardVersion)
          .liveLookupsEnabled(false)
          .build();
    } catch (Exception e) {
      p.addProblem(
              Problem.Severity.ERROR,
              "Error instantiating Google credentials: " + e.getMessage() + ".")
          .setRemediation(
              "Do the provided credentials have access to project "
                  + canaryAccount.getProject()
                  + "?");
      return null;
    }
  }

  public GcsProperties getGoogleCloudStorageProperties(GoogleCanaryAccount n) {
    GcsProperties gcsProperties = new GcsProperties();
    Path jsonPath = validatingFileDecryptPath(n.getJsonPath());
    gcsProperties.setJsonPath(jsonPath.toString());
    gcsProperties.setProject(n.getProject());
    gcsProperties.setBucket(n.getBucket());
    gcsProperties.setBucketLocation(n.getBucketLocation());
    return gcsProperties;
  }
}
