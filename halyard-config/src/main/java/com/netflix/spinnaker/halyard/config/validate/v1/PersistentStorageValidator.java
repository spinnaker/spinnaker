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
 *
 */

package com.netflix.spinnaker.halyard.config.validate.v1;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.GcsStorageService;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStorage;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.halyard.config.model.v1.node.Provider.ProviderType.GOOGLE;

@Component
public class PersistentStorageValidator extends Validator<PersistentStorage> {
  @Autowired
  private AccountService accountService;

  @Autowired
  private Registry registry;

  @Override
  public void validate(ConfigProblemSetBuilder p, PersistentStorage n) {
    String accountName = n.getAccountName();
    if (accountName == null || accountName.isEmpty()) {
      p.addProblem(Severity.WARNING, "You have not chosen an AWS or Google account to use as a persistent object store.");
      return;
    }

    String deploymentName = n.parentOfType(DeploymentConfiguration.class).getName();

    // TODO(lwander) This will need an AWS validation path as well once it's supported: https://github.com/spinnaker/halyard/issues/116
    GoogleAccount googleAccount = (GoogleAccount) accountService.getProviderAccount(deploymentName, GOOGLE.getId(), accountName);

    String jsonPath = googleAccount.getJsonPath();
    StorageService storageService = new GcsStorageService(
        n.getBucket(),
        n.getLocation(),
        n.getRootFolder(),
        googleAccount.getProject(),
        jsonPath != null ? jsonPath : "",
        "halyard",
        registry);

    try {
      storageService.ensureBucketExists();
    } catch (Exception e) {
      p.addProblem(Severity.ERROR, "Failed to ensure the required bucket \"" + n.getBucket() + "\" exists: " + e.getMessage());
    }
  }
}
