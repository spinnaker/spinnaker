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
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStorage;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.CommonGoogleAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PersistentStorageValidator extends Validator<PersistentStorage> {
  @Autowired
  private AccountService accountService;

  @Autowired
  private Registry registry;

  @Override
  public void validate(ConfigProblemSetBuilder ps, PersistentStorage n) {
    String accountName = n.getAccountName();
    if (accountName == null || accountName.isEmpty()) {
      ps.addProblem(Severity.ERROR, "You have not chosen an AWS or Google account to use as a persistent object store.", "accountName");
      return;
    }

    String deploymentName = n.parentOfType(DeploymentConfiguration.class).getName();
    StorageService storageService = null;
    try {
      Account account = accountService.getAnyProviderAccount(deploymentName, accountName);

      if (account instanceof CommonGoogleAccount) {
        storageService = buildStorageService(ps, n, (CommonGoogleAccount) account);
      } else if (account instanceof AwsAccount) {
        storageService = buildStorageService(ps, n, (AwsAccount) account) ;
      }
    } catch (HalException e) {
      ps.extend(e);
      return;
    }

    if (storageService == null) {
      throw new HalException(new ConfigProblemBuilder(Severity.FATAL,
          "You must supply either a GCE, or Appengine account to your persistent storage service").build()
      );
    }

    try {
      storageService.ensureBucketExists();
    } catch (Exception e) {
      ps.addProblem(Severity.ERROR, "Failed to ensure the required bucket \"" + n.getBucket() + "\" exists: " + e.getMessage());
    }
  }

  private StorageService buildStorageService(ConfigProblemSetBuilder ps, PersistentStorage n, CommonGoogleAccount googleAccount) {
    String jsonPath = googleAccount.getJsonPath();
    StorageService storageService = new GcsStorageService(
        n.getBucket(),
        n.getLocation(),
        n.getRootFolder(),
        googleAccount.getProject(),
        jsonPath != null ? jsonPath : "",
        "halyard",
        registry);

    return storageService;
  }

  private StorageService buildStorageService(ConfigProblemSetBuilder ps, PersistentStorage n, AwsAccount awsAccount) {
    return null;
  }
}
