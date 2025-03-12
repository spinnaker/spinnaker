/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.providers.cloudfoundry;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.cloudfoundry.CloudFoundryProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.springframework.stereotype.Component;

@Component
public class CloudFoundryProviderValidator extends Validator<CloudFoundryProvider> {
  @Override
  public void validate(ConfigProblemSetBuilder problemSetBuilder, CloudFoundryProvider provider) {
    CloudFoundryAccountValidator cloudFoundryAccountValidator = new CloudFoundryAccountValidator();
    if (provider.getClientConfig() != null
        && provider.getClientConfig().getMaxRetries() != null
        && provider.getClientConfig().getMaxRetries() < 1) {
      problemSetBuilder.addProblem(
          Problem.Severity.ERROR,
          "If provided, the max number of retries should be greater than 1.");
    }
    provider
        .getAccounts()
        .forEach(
            cloudFoundryAccount ->
                cloudFoundryAccountValidator.validate(problemSetBuilder, cloudFoundryAccount));
  }
}
