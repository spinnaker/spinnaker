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
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsub;
import com.netflix.spinnaker.halyard.config.model.v1.node.Subscription;
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
public class SubscriptionService {
  @Autowired private LookupService lookupService;

  @Autowired private PubsubService pubsubService;

  @Autowired private ValidateService validateService;

  @Autowired private OptionsService optionsService;

  public List<Subscription> getAllSubscriptions(String deploymentName, String pubsubName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setPubsub(pubsubName).withAnySubscription();

    List<Subscription> matchingSubscriptions =
        lookupService.getMatchingNodesOfType(filter, Subscription.class);

    if (matchingSubscriptions.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No subscriptions could be found").build());
    } else {
      return matchingSubscriptions;
    }
  }

  private Subscription getSubscription(NodeFilter filter, String subscriptionName) {
    List<Subscription> matchingSubscriptions =
        lookupService.getMatchingNodesOfType(filter, Subscription.class);

    switch (matchingSubscriptions.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "No subscription with name \"" + subscriptionName + "\" was found")
                .setRemediation(
                    "Check if this subscription was defined in another pubsub, or create a new one")
                .build());
      case 1:
        return matchingSubscriptions.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "More than one subscription named \"" + subscriptionName + "\" was found")
                .setRemediation(
                    "Manually delete/rename duplicate subscriptions with name \""
                        + subscriptionName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public Subscription getPubsubSubscription(
      String deploymentName, String pubsubName, String subscriptionName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setPubsub(pubsubName)
            .setSubscription(subscriptionName);
    return getSubscription(filter, subscriptionName);
  }

  public Subscription getAnyPubsubSubscription(String deploymentName, String subscriptionName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .withAnyPubsub()
            .setSubscription(subscriptionName);
    return getSubscription(filter, subscriptionName);
  }

  public void setSubscription(
      String deploymentName,
      String pubsubName,
      String subscriptionName,
      Subscription newSubscription) {
    Pubsub pubsub = pubsubService.getPubsub(deploymentName, pubsubName);

    for (int i = 0; i < pubsub.getSubscriptions().size(); i++) {
      Subscription subscription = (Subscription) pubsub.getSubscriptions().get(i);
      if (subscription.getNodeName().equals(subscriptionName)) {
        pubsub.getSubscriptions().set(i, newSubscription);
        return;
      }
    }

    throw new HalException(
        new ConfigProblemBuilder(
                Severity.FATAL, "Subscription \"" + subscriptionName + "\" wasn't found")
            .build());
  }

  public void deleteSubscription(
      String deploymentName, String pubsubName, String subscriptionName) {
    Pubsub pubsub = pubsubService.getPubsub(deploymentName, pubsubName);
    boolean removed =
        pubsub
            .getSubscriptions()
            .removeIf(
                subscription -> ((Subscription) subscription).getName().equals(subscriptionName));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Severity.FATAL, "Subscription \"" + subscriptionName + "\" wasn't found")
              .build());
    }
  }

  public void addSubscription(
      String deploymentName, String pubsubName, Subscription newSubscription) {
    Pubsub pubsub = pubsubService.getPubsub(deploymentName, pubsubName);
    pubsub.getSubscriptions().add(newSubscription);
  }

  public ProblemSet validateSubscription(
      String deploymentName, String pubsubName, String subscriptionName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setPubsub(pubsubName)
            .setSubscription(subscriptionName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllSubscriptions(String deploymentName, String pubsubName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setPubsub(pubsubName).withAnySubscription();
    return validateService.validateMatchingFilter(filter);
  }
}
