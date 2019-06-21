/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.providers;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.springframework.stereotype.Component;

@Component
public class AccountValidator extends Validator<Account> {

  @Override
  public void validate(ConfigProblemSetBuilder p, Account n) {
    if (n.getName() == null) {
      p.addProblem(Severity.FATAL, "Account name must be specified");
    }
    if (n.getRequiredGroupMembership() != null && !n.getRequiredGroupMembership().isEmpty()) {
      p.addProblem(
          Problem.Severity.WARNING,
          "requiredGroupMembership has been "
              + "deprecated. Please consider moving to using permissions with the flags --read-permissions "
              + "and --write-permissions instead. Read more at https://spinnaker.io/setup/security/authorization.");
    }
  }
}
