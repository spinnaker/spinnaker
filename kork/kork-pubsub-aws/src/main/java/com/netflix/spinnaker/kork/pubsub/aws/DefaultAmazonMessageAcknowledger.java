/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.pubsub.aws;

import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiptHandleIsInvalidException;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonMessageAcknowledger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultAmazonMessageAcknowledger implements AmazonMessageAcknowledger {
  private Registry registry;

  public DefaultAmazonMessageAcknowledger(Registry registry) {
    this.registry = registry;
  }

  @Override
  public void ack(AmazonSubscriptionInformation subscription, Message message) {
    // Delete from queue
    try {
      subscription.amazonSQS.deleteMessage(subscription.queueUrl, message.getReceiptHandle());
      registry.counter(getSuccessCounter(subscription)).increment();
    } catch (ReceiptHandleIsInvalidException e) {
      log.warn(
          "Error deleting message: {}, subscription: {}", message.getMessageId(), subscription, e);
      registry.counter(getErrorCounter(subscription, e)).increment();
    }
  }

  @Override
  public void nack(AmazonSubscriptionInformation subscription, Message message) {
    // Do nothing
    registry.counter(getNackCounter(subscription)).increment();
  }

  private Id getSuccessCounter(AmazonSubscriptionInformation subscription) {
    return registry.createId(
        "pubsub.amazon.acked", "subscription", subscription.properties.getName());
  }

  private Id getErrorCounter(AmazonSubscriptionInformation subscription, Exception e) {
    return registry.createId(
        "pubsub.amazon.ackFailed",
        "subscription",
        subscription.properties.getName(),
        "exceptionClass",
        e.getClass().getSimpleName());
  }

  private Id getNackCounter(AmazonSubscriptionInformation subscription) {
    return registry.createId(
        "pubsub.amazon.nacked", "subscription", subscription.properties.getName());
  }
}
