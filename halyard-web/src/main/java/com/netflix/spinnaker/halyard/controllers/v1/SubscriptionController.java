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
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsubs;
import com.netflix.spinnaker.halyard.config.model.v1.node.Subscription;
import com.netflix.spinnaker.halyard.config.services.v1.SubscriptionService;
import com.netflix.spinnaker.halyard.core.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/pubsubs/{pubsubName:.+}/subscriptions")
public class SubscriptionController {
  @Autowired
  SubscriptionService subscriptionService;

  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Subscription>> subscriptions(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<List<Subscription>> builder = new StaticRequestBuilder<>(
            () -> subscriptionService.getAllSubscriptions(deploymentName, pubsubName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> subscriptionService.validateAllSubscriptions(deploymentName, pubsubName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get all " + pubsubName + " subscriptions");
  }

  @RequestMapping(value = "/subscription/{subscriptionName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Subscription> subscription(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @PathVariable String subscriptionName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<Subscription> builder = new StaticRequestBuilder<>(
            () -> subscriptionService.getPubsubSubscription(deploymentName, pubsubName, subscriptionName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> subscriptionService.validateSubscription(deploymentName, pubsubName, subscriptionName));
    }

    return DaemonTaskHandler.submitTask(builder::build, "Get " + subscriptionName + " subscription");
  }

  @RequestMapping(value = "/subscription/{subscriptionName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteSubscription(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @PathVariable String subscriptionName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> subscriptionService.deleteSubscription(deploymentName, pubsubName, subscriptionName));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> subscriptionService.validateAllSubscriptions(deploymentName, pubsubName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Delete the " + subscriptionName + " subscription");
  }

  @RequestMapping(value = "/subscription/{subscriptionName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setSubscription(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @PathVariable String subscriptionName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawSubscription) {
    Subscription subscription = objectMapper.convertValue(
        rawSubscription,
        Pubsubs.translateSubscriptionType(pubsubName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> subscriptionService.setSubscription(deploymentName, pubsubName, subscriptionName, subscription));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> subscriptionService.validateSubscription(deploymentName, pubsubName, subscription.getName());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + subscriptionName + " subscription");
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addSubscription(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawSubscription) {
    Subscription subscription = objectMapper.convertValue(
        rawSubscription,
        Pubsubs.translateSubscriptionType(pubsubName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();
    builder.setSeverity(severity);

    builder.setUpdate(() -> subscriptionService.addSubscription(deploymentName, pubsubName, subscription));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> subscriptionService.validateSubscription(deploymentName, pubsubName, subscription.getName());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Add the " + subscription.getName() + " subscription");
  }
}
