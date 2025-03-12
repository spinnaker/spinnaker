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
 */

package com.netflix.spinnaker.halyard.config.validate.v1.providers.google;

import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleBakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleBaseImage;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@EqualsAndHashCode(callSuper = false)
@Data
public class GoogleBakeryDefaultsValidator extends Validator<GoogleBakeryDefaults> {
  private final List<GoogleNamedAccountCredentials> credentialsList;

  private final String halyardVersion;

  @Override
  public void validate(ConfigProblemSetBuilder p, GoogleBakeryDefaults n) {
    DaemonTaskHandler.message(
        "Validating "
            + n.getNodeName()
            + " with "
            + GoogleBakeryDefaultsValidator.class.getSimpleName());

    String zone = n.getZone();
    String network = n.getNetwork();
    String networkProjectId = n.getNetworkProjectId();
    List<GoogleBaseImage> baseImages = n.getBaseImages();

    if (StringUtils.isEmpty(zone)
        && StringUtils.isEmpty(network)
        && CollectionUtils.isEmpty(baseImages)) {
      return;
    } else if (CollectionUtils.isEmpty(credentialsList)) {
      return;
    }

    if (StringUtils.isEmpty(zone)) {
      p.addProblem(Problem.Severity.ERROR, "No zone supplied for google bakery defaults.");
    } else {
      int i = 0;
      boolean foundZone = false;

      while (!foundZone && i < credentialsList.size()) {
        GoogleNamedAccountCredentials credentials = credentialsList.get(i);

        try {
          credentials.getCompute().zones().get(credentials.getProject(), zone).execute();
          foundZone = true;
        } catch (Exception e) {
        }

        i++;
      }

      if (!foundZone) {
        p.addProblem(
            Problem.Severity.ERROR,
            "Zone " + zone + " not found via any configured google account.");
      }
    }

    if (StringUtils.isEmpty(network)) {
      p.addProblem(Problem.Severity.ERROR, "No network supplied for google bakery defaults.");
    } else {
      int j = 0;
      boolean foundNetwork = false;

      while (!foundNetwork && j < credentialsList.size()) {
        GoogleNamedAccountCredentials credentials = credentialsList.get(j);

        try {
          String project =
              !StringUtils.isEmpty(networkProjectId) ? networkProjectId : credentials.getProject();
          credentials.getCompute().networks().get(project, network).execute();
          foundNetwork = true;
        } catch (Exception e) {
        }

        j++;
      }

      if (!foundNetwork) {
        p.addProblem(
            Problem.Severity.ERROR,
            "Network " + network + " not found via any configured google account.");
      }
    }

    GoogleBaseImageValidator googleBaseImageValidator =
        new GoogleBaseImageValidator(credentialsList, halyardVersion);

    baseImages.forEach(googleBaseImage -> googleBaseImageValidator.validate(p, googleBaseImage));
  }
}
