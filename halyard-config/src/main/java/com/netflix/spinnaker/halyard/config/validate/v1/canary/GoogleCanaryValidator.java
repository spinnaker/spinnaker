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

package com.netflix.spinnaker.halyard.config.validate.v1.canary;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.halyard.config.model.v1.canary.GoogleCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Setter;

public class GoogleCanaryValidator extends Validator<GoogleCanaryServiceIntegration> {

  @Setter
  private String halyardVersion;

  @Setter
  private Registry registry;

  @Override
  public void validate(ConfigProblemSetBuilder p, GoogleCanaryServiceIntegration n) {
    GoogleCanaryAccountValidator googleCanaryAccountValidator = new GoogleCanaryAccountValidator(halyardVersion);

    n.getAccounts().forEach(a -> googleCanaryAccountValidator.validate(p, a));

    if (n.isGcsEnabled()) {
      CanaryGCSValidator canaryGCSValidator = new CanaryGCSValidator().setRegistry(registry);

      n.getAccounts().forEach(a -> canaryGCSValidator.validate(p, a));
    }
  }
}
