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

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.aws.ARN;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonMessageAcknowledger;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonPubsubMessageHandler;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubProperties;
import com.netflix.spinnaker.kork.pubsub.model.PubsubSubscriber;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One subscriber for each subscription. The subscriber makes sure the SQS queue is created,
 * subscribes to the SNS topic, polls the queue for messages, and removes them once processed.
 */
public class SQSSubscriber implements Runnable, PubsubSubscriber {
  private static final Logger log = LoggerFactory.getLogger(SQSSubscriber.class);

  private final AmazonSNS amazonSNS;
  private final AmazonSQS amazonSQS;
  private final AmazonPubsubProperties.AmazonPubsubSubscription subscription;
  private final AmazonPubsubMessageHandler messageHandler;
  private final AmazonMessageAcknowledger messageAcknowledger;
  private final Registry registry;
  private final Supplier<Boolean> isEnabled;

  private final ARN queueARN;
  private final ARN topicARN;
  private AmazonSubscriptionInformation subscriptionInfo;

  public SQSSubscriber(
      AmazonPubsubProperties.AmazonPubsubSubscription subscription,
      AmazonPubsubMessageHandler messageHandler,
      AmazonMessageAcknowledger messageAcknowledger,
      AmazonSNS amazonSNS,
      AmazonSQS amazonSQS,
      Supplier<Boolean> isEnabled,
      Registry registry) {
    this.subscription = subscription;
    this.messageHandler = messageHandler;
    this.messageAcknowledger = messageAcknowledger;
    this.amazonSNS = amazonSNS;
    this.amazonSQS = amazonSQS;
    this.isEnabled = isEnabled;
    this.registry = registry;

    this.queueARN = new ARN(subscription.getQueueARN());
    this.topicARN = new ARN(subscription.getTopicARN());
  }

  public String getWorkerName() {
    return queueARN.getArn() + "/" + SQSSubscriber.class.getSimpleName();
  }

  @Override
  public String getPubsubSystem() {
    return AmazonPubsubConfig.SYSTEM;
  }

  @Override
  public String getSubscriptionName() {
    return subscription.getName();
  }

  @Override
  public String getName() {
    return getSubscriptionName();
  }

  @Override
  public void run() {
    log.info("Starting {}", getWorkerName());
    try {
      initializeQueue();
    } catch (Exception e) {
      log.error("Error initializing queue {}", queueARN, e);
      throw e;
    }

    while (true) {
      try {
        listenForMessages();
      } catch (QueueDoesNotExistException e) {
        log.warn("Queue {} does not exist, recreating", queueARN, e);
        initializeQueue();
      } catch (Exception e) {
        log.error("Unexpected error running {}, restarting worker", getWorkerName(), e);
        try {
          Thread.sleep(500);
        } catch (InterruptedException e1) {
          log.error("Thread {} interrupted while sleeping", getWorkerName(), e1);
        }
      }
    }
  }

  private void initializeQueue() {
    String queueUrl =
        PubSubUtils.ensureQueueExists(
            amazonSQS, queueARN, topicARN, subscription.getSqsMessageRetentionPeriodSeconds());
    PubSubUtils.subscribeToTopic(amazonSNS, topicARN, queueARN);

    this.subscriptionInfo =
        AmazonSubscriptionInformation.builder()
            .amazonSNS(amazonSNS)
            .amazonSQS(amazonSQS)
            .properties(subscription)
            .queueUrl(queueUrl)
            .build();
  }

  private void listenForMessages() {
    while (isEnabled.get()) {
      ReceiveMessageResult receiveMessageResult =
          amazonSQS.receiveMessage(
              new ReceiveMessageRequest(this.subscriptionInfo.queueUrl)
                  .withMaxNumberOfMessages(subscription.getMaxNumberOfMessages())
                  .withVisibilityTimeout(subscription.getVisibilityTimeout())
                  .withWaitTimeSeconds(subscription.getWaitTimeSeconds())
                  .withMessageAttributeNames("All"));

      if (receiveMessageResult.getMessages().isEmpty()) {
        log.debug("Received no messages for queue {}", queueARN);
        continue;
      }

      receiveMessageResult.getMessages().forEach(this::handleMessage);
    }
  }

  private void handleMessage(Message message) {
    Exception caught = null;
    try {
      messageHandler.handleMessage(message);
      getSuccessCounter().increment();
    } catch (Exception e) {
      log.error("failed to process message {}", message, e);
      getErrorCounter(e).increment();
      caught = e;
    }

    if (caught == null) {
      messageAcknowledger.ack(subscriptionInfo, message);
    } else {
      messageAcknowledger.nack(subscriptionInfo, message);
    }
  }

  private Counter getSuccessCounter() {
    return registry.counter("pubsub.amazon.processed", "subscription", getSubscriptionName());
  }

  private Counter getErrorCounter(Exception e) {
    return registry.counter(
        "pubsub.amazon.failed",
        "subscription",
        getSubscriptionName(),
        "exceptionClass",
        e.getClass().getSimpleName());
  }
}
