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

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeploymentEnvironmentService {
  @Autowired private LookupService lookupService;

  @Autowired private DeploymentService deploymentService;

  @Autowired private ValidateService validateService;

  public DeploymentEnvironment getDeploymentEnvironment(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setDeploymentEnvironment();

    List<DeploymentEnvironment> matching =
        lookupService.getMatchingNodesOfType(filter, DeploymentEnvironment.class);

    switch (matching.size()) {
      case 0:
        DeploymentEnvironment deploymentEnvironment = new DeploymentEnvironment();
        setDeploymentEnvironment(deploymentName, deploymentEnvironment);
        return deploymentEnvironment;
      case 1:
        return matching.get(0);
      default:
        throw new RuntimeException(
            "It shouldn't be possible to have multiple deploymentEnvironment nodes. This is a bug.");
    }
  }

  public void setDeploymentEnvironment(
      String deploymentName, DeploymentEnvironment newDeploymentEnvironment) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setDeploymentEnvironment(newDeploymentEnvironment);
  }

  public ProblemSet validateDeploymentEnvironment(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setDeploymentEnvironment();

    return validateService.validateMatchingFilter(filter);
  }
}
