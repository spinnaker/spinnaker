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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStorage;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Front50Profile extends SpringProfile {
  @Autowired
  AccountService accountService;

  @Override
  public String getProfileName() {
    return "front50";
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.FRONT50;
  }

  @Override
  public ProfileConfig generateFullConfig(ProfileConfig config, DeploymentConfiguration deploymentConfiguration, SpinnakerEndpoints endpoints) {
    PersistentStorage storage = deploymentConfiguration.getPersistentStorage();
    Account account = accountService.getProviderAccount(deploymentConfiguration.getName(), "google", storage.getAccountName());
    Front50Credentials credentials = new Front50Credentials();

    if (account != null) {
      credentials.getSpinnaker().setGcs(new Front50Credentials.Spinnaker.GCS(storage, (GoogleAccount) account));
      config.setRequiredFiles(dependentFiles(account));
      config.appendConfig(yamlToString(credentials));
      return config;
    } else {
      throw new HalException(
          new ConfigProblemBuilder(Severity.FATAL, "No valid account configured for persistent storage.").build());
    }
  }

  @Data
  private static class Front50Credentials {
    Spinnaker spinnaker = new Spinnaker();

    @Data
    static class Spinnaker {
      GCS gcs = new GCS();
      S3 s3 = new S3();

      @Data
      static class GCS {
        private boolean enabled = false;
        private String bucket;
        private String bucketLocation;
        private String rootFolder;
        private String project;
        private String jsonPath;

        GCS() { }

        GCS(PersistentStorage storage, GoogleAccount account) {
          this.enabled = true;
          this.bucket = storage.getBucket();
          this.rootFolder = storage.getRootFolder();
          this.project = account.getProject();
          this.jsonPath = account.getJsonPath();
        }
      }

      @Data
      static class S3 {
        private boolean enabled = false;
        private String bucket;
        private String rootFolder;

        S3() {}

        S3(PersistentStorage storage) {
          // TODO(lwander) find someone to handle https://github.com/spinnaker/halyard/issues/116
        }
      }
    }
  }
}
