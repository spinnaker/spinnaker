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

package com.netflix.spinnaker.halyard.config.validate.v1.providers.google;

import com.google.api.services.compute.Compute;
import com.netflix.spinnaker.clouddriver.google.ComputeVersion;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.util.ValidatingFileReader;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Data
public class GoogleAccountValidator extends Validator<GoogleAccount> {
  final private List<GoogleNamedAccountCredentials> credentialsList;

  final private String halyardVersion;

  @Override
  public void validate(ConfigProblemSetBuilder p, GoogleAccount n) {
    DaemonTaskHandler.log("Validating " + n.getNodeName() + " with " + GoogleAccountValidator.class.getSimpleName());

    String jsonKey = null;
    String jsonPath = n.getJsonPath();
    String project = n.getProject();
    GoogleNamedAccountCredentials credentials = null;

    if (!StringUtils.isEmpty(jsonPath)) {
      jsonKey = ValidatingFileReader.contents(p, jsonPath);

      if (jsonKey == null) {
        return;
      } else if (jsonKey.isEmpty()) {
        p.addProblem(Severity.WARNING, "The supplied credentials file is empty.");
      }
    }

    if (StringUtils.isEmpty(n.getProject())) {
      p.addProblem(Severity.ERROR, "No google project supplied.");
      return;
    }

    try {
      credentials = new GoogleNamedAccountCredentials.Builder()
          .jsonKey(jsonKey)
          .project(n.getProject())
          .computeVersion(n.isAlphaListed() ? ComputeVersion.ALPHA : ComputeVersion.DEFAULT)
          .imageProjects(n.getImageProjects())
          .applicationName("halyard " + halyardVersion)
          .build();
      credentialsList.add(credentials);
    } catch (Exception e) {
      p.addProblem(Severity.ERROR, "Error instantiating Google credentials: " + e.getMessage() + ".")
        .setRemediation("Do the provided credentials have access to project " + n.getProject() + "?");
      return;
    }

    try {
      Compute compute = credentials.getCompute();

      compute.projects().get(project).execute();

      for (String imageProject : n.getImageProjects()) {
        compute.projects().get(imageProject).execute();
      }
    } catch (IOException e) {
      p.addProblem(Severity.ERROR, "Failed to load project \"" + n.getProject() + "\": " + e.getMessage() + ".");
    }
  }
}
