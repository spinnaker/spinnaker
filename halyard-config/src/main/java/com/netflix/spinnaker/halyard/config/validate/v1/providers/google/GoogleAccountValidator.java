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

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.clouddriver.google.ComputeVersion;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GoogleAccountValidator extends Validator<GoogleAccount> {
  @Autowired
  String halyardVersion;

  @Override
  public void validate(ProblemSetBuilder p, GoogleAccount n) {
    String jsonKey = null;
    String jsonPath = n.getJsonPath();
    String project = n.getProject();
    GoogleNamedAccountCredentials credentials = null;

    try {
      if (jsonPath != null && !jsonPath.isEmpty()) {
        jsonKey = IOUtils.toString(new FileInputStream(n.getJsonPath()));

        if (jsonKey.isEmpty()) {
          p.addProblem(Severity.WARNING, "The supplied credentials file is empty.");
        }
      }
    } catch (FileNotFoundException e) {
      p.addProblem(Severity.ERROR, "Json path not found: " + e.getMessage() + ".");
    } catch (IOException e) {
      p.addProblem(Severity.ERROR, "Error opening specified json path: " + e.getMessage() + ".");
    }

    if (n.getProject() == null || n.getProject().isEmpty()) {
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
    } catch (Exception e) {
      p.addProblem(Severity.ERROR, "Error instantiating Google credentials: " + e.getMessage() + ".");
      return;
    }

    try {
      credentials.getCompute().projects().get(project);

      for (String imageProject : n.getImageProjects()) {
        credentials.getCompute().projects().get(imageProject);
      }
    } catch (IOException e) {
      p.addProblem(Severity.ERROR, "Failed to load project \"" + n.getProject() + "\": " + e.getMessage() + ".");
    }
  }
}
