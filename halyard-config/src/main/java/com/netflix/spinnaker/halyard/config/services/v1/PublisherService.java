/*
 * Copyright 2019 Google, Inc.
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
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Publisher;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsub;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfig's deployments.
 */
@Component
public class PublisherService {
  @Autowired private LookupService lookupService;

  @Autowired private PubsubService pubsubService;

  @Autowired private ValidateService validateService;

  public List<Publisher> getAllPublishers(String deploymentName, String pubsubName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setPubsub(pubsubName).withAnyPublisher();

    List<Publisher> matchingPublishers =
        lookupService.getMatchingNodesOfType(filter, Publisher.class);

    if (matchingPublishers.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No publishers could be found").build());
    } else {
      return matchingPublishers;
    }
  }

  private Publisher getPublisher(NodeFilter filter, String publisherName) {
    List<Publisher> matchingPublishers =
        lookupService.getMatchingNodesOfType(filter, Publisher.class);

    switch (matchingPublishers.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "No publisher with name \"" + publisherName + "\" was found")
                .setRemediation(
                    "Check if this publisher was defined in another pubsub, or create a new one")
                .build());
      case 1:
        return matchingPublishers.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "More than one publisher named \"" + publisherName + "\" was found")
                .setRemediation(
                    "Manually delete/rename duplicate publishers with name \""
                        + publisherName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public Publisher getPubsubPublisher(
      String deploymentName, String pubsubName, String publisherName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setPubsub(pubsubName)
            .setPublisher(publisherName);
    return getPublisher(filter, publisherName);
  }

  public Publisher getAnyPubsubPublisher(String deploymentName, String publisherName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).withAnyPubsub().setPublisher(publisherName);
    return getPublisher(filter, publisherName);
  }

  public void setPublisher(
      String deploymentName, String pubsubName, String publisherName, Publisher newPublisher) {
    Pubsub pubsub = pubsubService.getPubsub(deploymentName, pubsubName);

    for (int i = 0; i < pubsub.getPublishers().size(); i++) {
      Publisher publisher = (Publisher) pubsub.getPublishers().get(i);
      if (publisher.getNodeName().equals(publisherName)) {
        pubsub.getPublishers().set(i, newPublisher);
        return;
      }
    }

    throw new HalException(
        new ConfigProblemBuilder(Severity.FATAL, "Publisher \"" + publisherName + "\" wasn't found")
            .build());
  }

  public void deletePublisher(String deploymentName, String pubsubName, String publisherName) {
    Pubsub pubsub = pubsubService.getPubsub(deploymentName, pubsubName);
    boolean removed =
        pubsub
            .getPublishers()
            .removeIf(publisher -> ((Publisher) publisher).getName().equals(publisherName));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Severity.FATAL, "Publisher \"" + publisherName + "\" wasn't found")
              .build());
    }
  }

  public void addPublisher(String deploymentName, String pubsubName, Publisher newPublisher) {
    Pubsub pubsub = pubsubService.getPubsub(deploymentName, pubsubName);
    pubsub.getPublishers().add(newPublisher);
  }

  public ProblemSet validatePublisher(
      String deploymentName, String pubsubName, String publisherName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setPubsub(pubsubName)
            .setPublisher(publisherName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllPublishers(String deploymentName, String pubsubName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setPubsub(pubsubName).withAnyPublisher();
    return validateService.validateMatchingFilter(filter);
  }
}
