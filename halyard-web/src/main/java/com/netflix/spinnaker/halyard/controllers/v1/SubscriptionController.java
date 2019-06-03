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

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsubs;
import com.netflix.spinnaker.halyard.config.model.v1.node.Subscription;
import com.netflix.spinnaker.halyard.config.services.v1.SubscriptionService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/pubsubs/{pubsubName:.+}/subscriptions")
public class SubscriptionController {
  private final SubscriptionService subscriptionService;
  private final HalconfigParser halconfigParser;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Subscription>> subscriptions(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<Subscription>>builder()
        .getter(() -> subscriptionService.getAllSubscriptions(deploymentName, pubsubName))
        .validator(() -> subscriptionService.validateAllSubscriptions(deploymentName, pubsubName))
        .description("Get all " + pubsubName + " subscriptions")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/subscription/{subscriptionName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Subscription> subscription(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @PathVariable String subscriptionName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Subscription>builder()
        .getter(
            () ->
                subscriptionService.getPubsubSubscription(
                    deploymentName, pubsubName, subscriptionName))
        .validator(
            () ->
                subscriptionService.validateSubscription(
                    deploymentName, pubsubName, subscriptionName))
        .description("Get " + subscriptionName + " subscription")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/subscription/{subscriptionName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteSubscription(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @PathVariable String subscriptionName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(
            () ->
                subscriptionService.deleteSubscription(
                    deploymentName, pubsubName, subscriptionName))
        .validator(() -> subscriptionService.validateAllSubscriptions(deploymentName, pubsubName))
        .description("Delete the " + subscriptionName + " subscription")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/subscription/{subscriptionName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setSubscription(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @PathVariable String subscriptionName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawSubscription) {
    Subscription subscription =
        objectMapper.convertValue(rawSubscription, Pubsubs.translateSubscriptionType(pubsubName));
    return GenericUpdateRequest.<Subscription>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(
            s ->
                subscriptionService.setSubscription(
                    deploymentName, pubsubName, subscriptionName, s))
        .validator(
            () ->
                subscriptionService.validateSubscription(
                    deploymentName, pubsubName, subscription.getName()))
        .description("Edit the " + subscriptionName + " subscription")
        .build()
        .execute(validationSettings, subscription);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addSubscription(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawSubscription) {
    Subscription subscription =
        objectMapper.convertValue(rawSubscription, Pubsubs.translateSubscriptionType(pubsubName));
    return GenericUpdateRequest.<Subscription>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(s -> subscriptionService.addSubscription(deploymentName, pubsubName, s))
        .validator(
            () ->
                subscriptionService.validateSubscription(
                    deploymentName, pubsubName, subscription.getName()))
        .description("Add the " + subscription.getName() + " subscription")
        .build()
        .execute(validationSettings, subscription);
  }
}
