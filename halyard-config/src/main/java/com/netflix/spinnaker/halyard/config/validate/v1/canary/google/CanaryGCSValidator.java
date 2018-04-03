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
import com.netflix.spinnaker.front50.model.GcsStorageService;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import lombok.Setter;

public class CanaryGCSValidator extends Validator<GoogleCanaryAccount> {

  @Setter
  private Registry registry;

  @Override
  public void validate(ConfigProblemSetBuilder ps, GoogleCanaryAccount n) {
    String jsonPath = n.getJsonPath();
    try {
      StorageService storageService = new GcsStorageService(
          n.getBucket(),
          n.getBucketLocation(),
          n.getRootFolder(),
          n.getProject(),
          jsonPath != null ? jsonPath : "",
          "halyard",
          registry);

      storageService.ensureBucketExists();
    } catch (Exception e) {
      e.printStackTrace();
      ps.addProblem(Severity.ERROR, "Failed to ensure the required canary bucket \"" + n.getBucket() + "\" exists: " + e.getMessage());
    }
  }
}