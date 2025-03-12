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

package com.netflix.spinnaker.halyard.config.validate.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HalconfigValidator extends Validator<Halconfig> {
  @Autowired VersionsService versionsService;

  @Override
  public void validate(ConfigProblemSetBuilder p, Halconfig n) {
    try {
      String runningVersion = versionsService.getRunningHalyardVersion();
      String latestVersion = versionsService.getLatestHalyardVersion();

      if (StringUtils.isEmpty(latestVersion)) {
        log.warn("No latest version of halyard published.");
        return;
      }

      if (runningVersion.contains("SNAPSHOT")) {
        return;
      }

      if (Versions.lessThan(runningVersion, latestVersion)) {
        ConfigProblemBuilder problemBuilder =
            p.addProblem(
                Problem.Severity.WARNING,
                "There is a newer version of Halyard available ("
                    + latestVersion
                    + "), please update when possible");

        File updateScript = new File("/usr/local/bin/update-halyard");
        if (updateScript.exists() && !updateScript.isDirectory()) {
          problemBuilder.setRemediation("Run 'sudo update-halyard' to upgrade");
        } else {
          problemBuilder.setRemediation(
              "Run 'sudo apt-get update && sudo apt-get install spinnaker-halyard -y' to upgrade");
        }
      }
    } catch (Exception e) {
      log.warn("Unexpected error comparing versions: " + e);
    }
  }
}
