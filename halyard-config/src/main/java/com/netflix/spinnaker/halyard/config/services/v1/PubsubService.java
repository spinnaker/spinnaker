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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsub;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsubs;
import com.netflix.spinnaker.halyard.config.model.v1.pubsub.google.GooglePubsub;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfigs pubsubs.
 */
@Component
public class PubsubService {
  @Autowired private LookupService lookupService;

  @Autowired private ValidateService validateService;

  @Autowired private DeploymentService deploymentService;

  public Pubsub getPubsub(String deploymentName, String pubsubName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setPubsub(pubsubName);

    List<Pubsub> matching = lookupService.getMatchingNodesOfType(filter, Pubsub.class);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "No pubsub with name \"" + pubsubName + "\" could be found")
                .setRemediation("Create a new pubsub with name \"" + pubsubName + "\"")
                .build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "More than one pubsub with name \"" + pubsubName + "\" found")
                .setRemediation(
                    "Manually delete or rename duplicate pubsubs with name \""
                        + pubsubName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public List<Pubsub> getAllPubsubs(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyPubsub();

    List<Pubsub> matching = lookupService.getMatchingNodesOfType(filter, Pubsub.class);

    if (matching.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No pubsubs could be found").build());
    } else {
      return matching;
    }
  }

  public void setPubsub(String deploymentName, Pubsub pubsub) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    Pubsubs pubsubs = deploymentConfiguration.getPubsub();
    switch (pubsub.getPubsubType()) {
      case GOOGLE:
        pubsubs.setGoogle((GooglePubsub) pubsub);
        break;
      default:
        throw new IllegalArgumentException("Unknown pubsub type " + pubsub.getPubsubType());
    }
  }

  public void setEnabled(String deploymentName, String pubsubName, boolean enabled) {
    Pubsub pubsub = getPubsub(deploymentName, pubsubName);
    pubsub.setEnabled(enabled);
  }

  public ProblemSet validatePubsub(String deploymentName, String pubsubName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setPubsub(pubsubName)
            .withAnySubscription()
            .setBakeryDefaults()
            .withAnyBaseImage();

    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllPubsubs(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).withAnyPubsub().withAnySubscription();

    return validateService.validateMatchingFilter(filter);
  }
}
