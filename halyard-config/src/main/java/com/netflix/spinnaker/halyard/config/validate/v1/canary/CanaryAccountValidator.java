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

import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CanaryAccountValidator<T extends AbstractCanaryAccount> extends Validator<T> {
  private static final String namePattern = "^[a-z0-9]+([-a-z0-9_]*[a-z0-9])?$";

  @Override
  public void validate(ConfigProblemSetBuilder p, T n) {
    if (n.getName() == null) {
      p.addProblem(Severity.FATAL, "Canary account name must be specified");
    } else if (!Pattern.matches(namePattern, n.getName())) {
      p.addProblem(Severity.ERROR, "Canary account name must match pattern " + namePattern)
          .setRemediation(
              "It must start and end with a lower-case character or number, and only contain lower-case characters, numbers, dashes, or underscores");
    }
  }
}
