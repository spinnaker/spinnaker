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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.errors.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.errors.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfigs
 * deployments.
 */
@Component
public class DeploymentService {
  @Autowired
  private LookupService lookupService;

  @Autowired
  private ValidateService validateService;

  public DeploymentConfiguration getDeploymentConfiguration(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName);

    List<DeploymentConfiguration> matching = lookupService.getMatchingNodesOfType(filter, DeploymentConfiguration.class);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(new ConfigProblemBuilder(Severity.FATAL,
            "No deployment with name \"" + deploymentName + "\" could be found")
            .setRemediation("Create a new deployment with name \"" + deploymentName + "\"").build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(new ConfigProblemBuilder(Severity.FATAL,
            "More than one deployment with name \"" + deploymentName + "\" found")
            .setRemediation("Manually delete or rename duplicate deployments with name \"" + deploymentName + "\" in your halconfig file").build());
    }
  }

  public List<DeploymentConfiguration> getAllDeploymentConfigurations() {
    NodeFilter filter = new NodeFilter().withAnyDeployment();

    List<DeploymentConfiguration> matching = lookupService.getMatchingNodesOfType(filter, DeploymentConfiguration.class);

    if (matching.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No deployments could be found in your currently loaded halconfig")
              .build());
    } else {
      return matching;
    }
  }

  public ProblemSet validateAllDeployments(Severity severity) {
    NodeFilter filter = new NodeFilter()
        .withAnyDeployment()
        .withAnyProvider()
        .withAnyAccount();

    return validateService.validateMatchingFilter(filter, severity);
  }

  public ProblemSet validateDeployment(String deploymentName, Severity severity) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyProvider().withAnyAccount();

    return validateService.validateMatchingFilter(filter, severity);
  }
}
