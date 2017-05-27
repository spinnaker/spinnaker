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
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.config.validate.v1.util.ValidatingFileReader;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class GoogleAccountValidator extends Validator<GoogleAccount> {
  final private List<GoogleNamedAccountCredentials> credentialsList;

  final private String halyardVersion;

  @Override
  public void validate(ConfigProblemSetBuilder p, GoogleAccount n) {
    DaemonTaskHandler.message("Validating " + n.getNodeName() + " with " + GoogleAccountValidator.class.getSimpleName());

    GoogleNamedAccountCredentials credentials = n.getNamedAccountCredentials(halyardVersion, p);
    if (credentials == null) {
      return;
    } else {
      credentialsList.add(credentials);
    }

    try {
      Compute compute = credentials.getCompute();

      compute.projects().get(n.getProject()).execute();

      for (String imageProject : n.getImageProjects()) {
        compute.projects().get(imageProject).execute();
      }
    } catch (IOException e) {
      p.addProblem(Severity.ERROR, "Failed to load project \"" + n.getProject() + "\": " + e.getMessage() + ".");
    }

    String userDataFile = null;
    if (!StringUtils.isEmpty(n.getUserDataFile())) {
      userDataFile = ValidatingFileReader.contents(p, n.getUserDataFile());

      if (userDataFile == null) {
        return;
      } else if (userDataFile.isEmpty()) {
        p.addProblem(Severity.WARNING, "The supplied user data file is empty.");
      }
    }

  }
}