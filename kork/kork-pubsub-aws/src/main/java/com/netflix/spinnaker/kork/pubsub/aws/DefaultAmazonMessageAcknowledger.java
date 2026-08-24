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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonMessageAcknowledger;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SqsException;

@Slf4j
public class DefaultAmazonMessageAcknowledger implements AmazonMessageAcknowledger {
  private final Registry registry;

  public DefaultAmazonMessageAcknowledger(Registry registry) {
    this.registry = registry;
  }

  @Override
  public void ack(AmazonSubscriptionInformation subscription, Message message) {
    try {
      subscription
          .getSqsClient()
          .deleteMessage(
              r -> r.queueUrl(subscription.getQueueUrl()).receiptHandle(message.receiptHandle()));
      registry.counter(getSuccessCounter(subscription)).increment();
    } catch (SqsException e) {
      log.warn(
          "Error deleting message: {}, subscription: {}", message.messageId(), subscription, e);
      registry.counter(getErrorCounter(subscription, e)).increment();
    }
  }

  @Override
  public void nack(AmazonSubscriptionInformation subscription, Message message) {
    // Do nothing — message will become visible again after visibility timeout
    registry.counter(getNackCounter(subscription)).increment();
  }

  private Id getSuccessCounter(AmazonSubscriptionInformation subscription) {
    return registry.createId(
        "pubsub.amazon.acked", "subscription", subscription.getProperties().getName());
  }

  private Id getErrorCounter(AmazonSubscriptionInformation subscription, Exception e) {
    return registry.createId(
        "pubsub.amazon.ackFailed",
        "subscription",
        subscription.getProperties().getName(),
        "exceptionClass",
        e.getClass().getSimpleName());
  }

  private Id getNackCounter(AmazonSubscriptionInformation subscription) {
    return registry.createId(
        "pubsub.amazon.nacked", "subscription", subscription.getProperties().getName());
  }
}
