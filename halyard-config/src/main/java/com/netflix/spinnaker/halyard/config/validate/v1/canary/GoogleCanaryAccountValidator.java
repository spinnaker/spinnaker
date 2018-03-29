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

import com.google.api.services.compute.Compute;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.GoogleCanaryAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.IOException;

@Data
@EqualsAndHashCode(callSuper = false)
public class GoogleCanaryAccountValidator extends CanaryAccountValidator {
  final private String halyardVersion;

  @Override
  public void validate(ConfigProblemSetBuilder p, AbstractCanaryAccount n) {
    super.validate(p, n);

    GoogleCanaryAccount canaryAccount = (GoogleCanaryAccount)n;

    DaemonTaskHandler.message("Validating " + n.getNodeName() + " with " + GoogleCanaryAccountValidator.class.getSimpleName());

    GoogleNamedAccountCredentials credentials = canaryAccount.getNamedAccountCredentials(halyardVersion, p);

    if (credentials == null) {
      return;
    }

    try {
      Compute compute = credentials.getCompute();

      compute.projects().get(canaryAccount.getProject()).execute();
    } catch (IOException e) {
      p.addProblem(Severity.ERROR, "Failed to load project \"" + canaryAccount.getProject() + "\": " + e.getMessage() + ".");
    }
  }
}