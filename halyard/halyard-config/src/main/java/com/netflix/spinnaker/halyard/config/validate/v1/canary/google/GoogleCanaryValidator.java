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
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.CollectionUtils;

public class GoogleCanaryValidator extends Validator<GoogleCanaryServiceIntegration> {

  @Setter private String halyardVersion;

  @Setter private Registry registry;

  @Setter TaskScheduler taskScheduler;

  public GoogleCanaryValidator(SecretSessionManager secretSessionManager) {
    this.secretSessionManager = secretSessionManager;
  }

  @Override
  public void validate(ConfigProblemSetBuilder p, GoogleCanaryServiceIntegration n) {
    GoogleCanaryAccountValidator googleCanaryAccountValidator =
        new GoogleCanaryAccountValidator(secretSessionManager)
            .setHalyardVersion(halyardVersion)
            .setRegistry(registry)
            .setTaskScheduler(taskScheduler);

    if (n.isGcsEnabled()) {
      List<GoogleCanaryAccount> accountsWithBucket =
          n.getAccounts().stream()
              .filter(a -> StringUtils.isNotEmpty(a.getBucket()))
              .collect(Collectors.toList());

      if (CollectionUtils.isEmpty(accountsWithBucket)) {
        p.addProblem(
            Problem.Severity.ERROR,
            "At least one Google account must specify a bucket if GCS is enabled.");
      } else {
        accountsWithBucket.forEach(a -> googleCanaryAccountValidator.validate(p, a));
      }
    }
  }
}
