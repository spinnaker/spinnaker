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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfigs
 * cis.
 */
@Component
public class CiService {
  @Autowired
  private LookupService lookupService;

  @Autowired
  private ValidateService validateService;

  public Ci getCi(String deploymentName, String ciName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName);

    List<Ci> matching = lookupService.getMatchingNodesOfType(filter, Ci.class);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(new ConfigProblemBuilder(Severity.FATAL,
            "No Continuous Integration service with name \"" + ciName + "\" could be found").build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(new ConfigProblemBuilder(Severity.FATAL,
            "More than one CI with name \"" + ciName + "\" found").build());
    }
  }

  public List<Ci> getAllCis(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyCi();

    List<Ci> matching = lookupService.getMatchingNodesOfType(filter, Ci.class);

    if (matching.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No cis could be found")
              .build());
    } else {
      return matching;
    }
  }

  public void setEnabled(String deploymentName, String ciName, boolean enabled) {
    Ci ci = getCi(deploymentName, ciName);
    ci.setEnabled(enabled);
  }

  public ProblemSet validateCi(String deploymentName, String ciName) {
    NodeFilter filter = new NodeFilter()
        .setDeployment(deploymentName)
        .setCi(ciName)
        .withAnyAccount();

    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllCis(String deploymentName) {
    NodeFilter filter = new NodeFilter()
        .setDeployment(deploymentName)
        .withAnyCi()
        .withAnyAccount();

    return validateService.validateMatchingFilter(filter);
  }
}
