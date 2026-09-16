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

import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonMessageAcknowledger;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SqsException;

@Slf4j
public class DefaultAmazonMessageAcknowledger implements AmazonMessageAcknowledger {
  private final MeterRegistry registry;

  public DefaultAmazonMessageAcknowledger(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void ack(AmazonSubscriptionInformation subscription, Message message) {
    try {
      subscription
          .getSqsClient()
          .deleteMessage(
              r -> r.queueUrl(subscription.getQueueUrl()).receiptHandle(message.receiptHandle()));
      incrementSuccessCounter(subscription);
    } catch (SqsException e) {
      log.warn(
          "Error deleting message: {}, subscription: {}", message.messageId(), subscription, e);
      incrementErrorCounter(subscription, e);
    }
  }

  @Override
  public void nack(AmazonSubscriptionInformation subscription, Message message) {
    // Do nothing — message will become visible again after visibility timeout
    incrementNackCounter(subscription);
  }

  private void incrementSuccessCounter(AmazonSubscriptionInformation subscription) {
    registry
        .counter("pubsub.amazon.acked", "subscription", subscription.getProperties().getName())
        .increment();
  }

  private void incrementErrorCounter(AmazonSubscriptionInformation subscription, Exception e) {
    registry
        .counter(
            "pubsub.amazon.ackFailed",
            "subscription",
            subscription.getProperties().getName(),
            "exceptionClass",
            e.getClass().getSimpleName())
        .increment();
  }

  private void incrementNackCounter(AmazonSubscriptionInformation subscription) {
    registry
        .counter("pubsub.amazon.nacked", "subscription", subscription.getProperties().getName())
        .increment();
  }
}
