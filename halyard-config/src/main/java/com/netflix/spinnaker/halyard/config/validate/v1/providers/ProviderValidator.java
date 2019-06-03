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
 *
 */

package com.netflix.spinnaker.halyard.config.validate.v1.providers;

import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.springframework.stereotype.Component;

@Component
public class ProviderValidator extends Validator<Provider> {
  @Override
  public void validate(ConfigProblemSetBuilder p, Provider n) {
    if (n.isEnabled()) {
      if (n.getAccounts() == null || n.getAccounts().isEmpty()) {
        p.addProblem(
            Problem.Severity.WARNING,
            "Provider " + n.getNodeName() + " is enabled, but no accounts have been configured.");
      }
    }
  }
}
