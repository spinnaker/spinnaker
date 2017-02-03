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
 */

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Features;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class FeaturesService {
  @Autowired
  LookupService lookupService;

  @Autowired
  DeploymentService deploymentService;

  public Features getFeatures(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setFeatures();

    List<Features> matching = lookupService.getMatchingNodesOfType(filter, Features.class)
        .stream()
        .map(n -> (Features) n)
        .collect(Collectors.toList());

    switch (matching.size()) {
      case 0:
        throw new RuntimeException("No features were found. This is a bug.");
      case 1:
        return matching.get(0);
      default:
        throw new RuntimeException("It shouldn't be possible to have multiple features nodes. This is a bug.");
    }
  }

  public void setFeatures(String deploymentName, Features newFeatures) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setFeatures(newFeatures);
  }
}
