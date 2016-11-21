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

import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.errors.v1.config.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeReference;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfigs
 * deployments.
 */
@Component
public class DeploymentService {
  @Autowired
  LookupService lookupService;

  public DeploymentConfiguration getDeploymentConfiguration(NodeReference reference) {
    String deploymentName = reference.getDeployment();
    NodeFilter filter = new NodeFilter(reference).withAnyHalconfigFile();

    List<DeploymentConfiguration> matching = lookupService.getMatchingNodesOfType(filter, DeploymentConfiguration.class)
            .stream()
            .map(n -> (DeploymentConfiguration) n)
            .collect(Collectors.toList());

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(new ProblemBuilder(Problem.Severity.FATAL,
            "No deployment with name \"" + deploymentName + "\" could be found")
            .setReference(reference)
            .setRemediation("Create a new deployment with name \"" + deploymentName + "\"").build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(new ProblemBuilder(Problem.Severity.FATAL,
            "More than one deployment with name \"" + deploymentName + "\" found")
            .setReference(reference)
            .setRemediation("Manually delete or rename duplicate deployments with name \"" + deploymentName + "\" in your halconfig file").build());
    }
  }

  public List<DeploymentConfiguration> getAllDeploymentConfigurations() {
    NodeFilter filter = NodeFilter.makeRejectAllFilter().withAnyHalconfigFile().withAnyDeployment();

    List<DeploymentConfiguration> matching = lookupService.getMatchingNodesOfType(filter, DeploymentConfiguration.class)
        .stream()
        .map(n -> (DeploymentConfiguration) n)
        .collect(Collectors.toList());

    if (matching.size() == 0) {
      throw new ConfigNotFoundException(
          new ProblemBuilder(Problem.Severity.FATAL, "No deployments could be found in your currently loaded halconfig")
              .build());
    } else {
      return matching;
    }
  }
}
