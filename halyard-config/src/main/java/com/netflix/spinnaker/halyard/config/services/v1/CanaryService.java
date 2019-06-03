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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CanaryService {

  @Autowired private LookupService lookupService;

  @Autowired private DeploymentService deploymentService;

  @Autowired private ValidateService validateService;

  public Canary getCanary(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCanary();

    return lookupService.getSingularNodeOrDefault(
        filter, Canary.class, Canary::new, n -> setCanary(deploymentName, n));
  }

  public void setCanaryEnabled(String deploymentName, boolean enabled) {
    Canary canary = getCanary(deploymentName);
    canary.setEnabled(enabled);
  }

  public void setCanary(String deploymentName, Canary newCanary) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setCanary(newCanary);
  }

  public ProblemSet validateCanary(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCanary();
    return validateService.validateMatchingFilter(filter);
  }
}
