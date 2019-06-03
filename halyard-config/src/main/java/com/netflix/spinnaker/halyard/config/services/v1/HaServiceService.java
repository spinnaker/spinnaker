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
 *
 *
 */

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.ha.ClouddriverHaService;
import com.netflix.spinnaker.halyard.config.model.v1.ha.EchoHaService;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaService;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaServices;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfig deployment environment's high availability services.
 */
@Component
public class HaServiceService {
  @Autowired private LookupService lookupService;

  @Autowired private ValidateService validateService;

  @Autowired private DeploymentService deploymentService;

  public HaService getHaService(String deploymentName, String serviceName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setDeploymentEnvironment()
            .setHaService(serviceName);

    List<HaService> matching = lookupService.getMatchingNodesOfType(filter, HaService.class);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "No high availability service with name \"" + serviceName + "\" could be found")
                .setRemediation(
                    "Create a new high availability service with name \"" + serviceName + "\"")
                .build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "More than one high availability service with name \""
                        + serviceName
                        + "\" found")
                .setRemediation(
                    "Manually delete or rename duplicate high availability services with name \""
                        + serviceName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public List<HaService> getAllHaServices(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyHaService();

    List<HaService> matching = lookupService.getMatchingNodesOfType(filter, HaService.class);

    if (matching.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No high availability services could be found")
              .build());
    } else {
      return matching;
    }
  }

  public void setHaService(String deploymentName, HaService haService) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    HaServices haServices = deploymentConfiguration.getDeploymentEnvironment().getHaServices();
    switch (haService.haServiceType()) {
      case CLOUDDRIVER:
        haServices.setClouddriver((ClouddriverHaService) haService);
        break;
      case ECHO:
        haServices.setEcho((EchoHaService) haService);
        break;
      default:
        throw new IllegalArgumentException(
            "Unknown high availability service type " + haService.haServiceType());
    }
  }

  public void setEnabled(String deploymentName, String serviceName, boolean enabled) {
    HaService haService = getHaService(deploymentName, serviceName);
    haService.setEnabled(enabled);
  }

  public ProblemSet validateHaService(String deploymentName, String serviceName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setDeploymentEnvironment()
            .setHaService(serviceName);

    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllHaServices(String deploymentName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setDeploymentEnvironment()
            .withAnyHaService();

    return validateService.validateMatchingFilter(filter);
  }
}
