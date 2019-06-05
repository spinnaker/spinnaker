/*
 * Copyright 2018 Netflix, Inc.
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
 */

package com.netflix.spinnaker.echo.pubsub.aws;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.config.AmazonPubsubProperties;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.utils.NodeIdentity;
import com.netflix.spinnaker.kork.aws.ARN;
import com.netflix.spinnaker.kork.aws.pubsub.PubSubUtils;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One subscriber for each subscription. The subscriber makes sure the SQS queue is created,
 * subscribes to the SNS topic, polls the queue for messages, and removes them once processed.
 */
public class SQSSubscriber implements Runnable, PubsubSubscriber {

  private static final Logger log = LoggerFactory.getLogger(SQSSubscriber.class);

  private static final int AWS_MAX_NUMBER_OF_MESSAGES = 10;
  private static final PubsubSystem pubsubSystem = PubsubSystem.AMAZON;

  private final ObjectMapper objectMapper;
  private final AmazonSNS amazonSNS;
  private final AmazonSQS amazonSQS;

  private final AmazonPubsubProperties.AmazonPubsubSubscription subscription;

  private final PubsubMessageHandler pubsubMessageHandler;

  private final NodeIdentity identity = new NodeIdentity();

  private final Registry registry;

  private final ARN queueARN;
  private final ARN topicARN;

  private String queueId = null;

  private final Supplier<Boolean> isEnabled;

  public SQSSubscriber(
      ObjectMapper objectMapper,
      AmazonPubsubProperties.AmazonPubsubSubscription subscription,
      PubsubMessageHandler pubsubMessageHandler,
      AmazonSNS amazonSNS,
      AmazonSQS amazonSQS,
      Supplier<Boolean> isEnabled,
      Registry registry) {
    this.objectMapper = objectMapper;
    this.subscription = subscription;
    this.pubsubMessageHandler = pubsubMessageHandler;
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
  public PubsubSystem getPubsubSystem() {
    return pubsubSystem;
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
    log.info("Starting " + getWorkerName());
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
        log.warn("Queue {} does not exist, recreating", queueARN);
        initializeQueue();
      } catch (Exception e) {
        log.error("Unexpected error running " + getWorkerName() + ", restarting worker", e);
        try {
          Thread.sleep(500);
        } catch (InterruptedException e1) {
          log.error("Thread {} interrupted while sleeping", getWorkerName(), e1);
        }
      }
    }
  }

  private void initializeQueue() {
    this.queueId =
        PubSubUtils.ensureQueueExists(
            amazonSQS, queueARN, topicARN, subscription.getSqsMessageRetentionPeriodSeconds());
    PubSubUtils.subscribeToTopic(amazonSNS, topicARN, queueARN);
  }

  private void listenForMessages() {
    while (isEnabled.get()) {
      ReceiveMessageResult receiveMessageResult =
          amazonSQS.receiveMessage(
              new ReceiveMessageRequest(queueId)
                  .withMaxNumberOfMessages(AWS_MAX_NUMBER_OF_MESSAGES)
                  .withVisibilityTimeout(subscription.getVisibilityTimeout())
                  .withWaitTimeSeconds(subscription.getWaitTimeSeconds())
                  .withMessageAttributeNames("All"));

      if (receiveMessageResult.getMessages().isEmpty()) {
        log.debug("Received no messages for queue: {}", queueARN);
        continue;
      }

      receiveMessageResult.getMessages().forEach(this::handleMessage);
    }
  }

  private void handleMessage(Message message) {
    try {
      String messageId = message.getMessageId();
      String messagePayload = unmarshalMessageBody(message.getBody());

      Map<String, String> stringifiedMessageAttributes =
          message.getMessageAttributes().entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));

      // SNS message attributes are stored within the SQS message body. Add them to other
      // attributes..
      Map<String, MessageAttributeWrapper> messageAttributes =
          unmarshalMessageAttributes(message.getBody());
      stringifiedMessageAttributes.putAll(
          messageAttributes.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAttributeValue())));

      MessageDescription description =
          MessageDescription.builder()
              .subscriptionName(getSubscriptionName())
              .messagePayload(messagePayload)
              .messageAttributes(stringifiedMessageAttributes)
              .pubsubSystem(pubsubSystem)
              .ackDeadlineSeconds(60) // Set a high upper bound on message processing time.
              .retentionDeadlineSeconds(
                  subscription.getDedupeRetentionSeconds()) // Configurable but default to 1 hour
              .build();

      AmazonMessageAcknowledger acknowledger =
          new AmazonMessageAcknowledger(amazonSQS, queueId, message, registry, getName());

      if (subscription.getAlternateIdInMessageAttributes() != null
          && !subscription.getAlternateIdInMessageAttributes().isEmpty()
          && stringifiedMessageAttributes.containsKey(
              subscription.getAlternateIdInMessageAttributes())) {
        // Message attributes contain the unique id used for deduping
        messageId =
            stringifiedMessageAttributes.get(subscription.getAlternateIdInMessageAttributes());
      }

      pubsubMessageHandler.handleMessage(
          description, acknowledger, identity.getIdentity(), messageId);
    } catch (Exception e) {
      registry.counter(getFailedToBeHandledMetricId(e)).increment();
      log.error("Message {} from queue {} failed to be handled", message, queueId, e);
      // Todo emjburns: add dead-letter queue policy
    }
  }

  private String unmarshalMessageBody(String messageBody) {
    String messagePayload = messageBody;
    try {
      NotificationMessageWrapper wrapper =
          objectMapper.readValue(messagePayload, NotificationMessageWrapper.class);
      if (wrapper != null && wrapper.getMessage() != null) {
        messagePayload = wrapper.getMessage();
      }
    } catch (IOException e) {
      // Try to unwrap a notification message; if that doesn't work,
      // we're dealing with a message we can't parse. The template or
      // the pipeline potentially knows how to deal with it.
      log.error(
          "Unable unmarshal NotificationMessageWrapper. Unknown message type. (body: {})",
          messageBody,
          e);
    }
    return messagePayload;
  }

  /**
   * If there is an error parsing message attributes because the message is not a notification
   * message, an empty map will be returned.
   */
  private Map<String, MessageAttributeWrapper> unmarshalMessageAttributes(String messageBody) {
    try {
      NotificationMessageWrapper wrapper =
          objectMapper.readValue(messageBody, NotificationMessageWrapper.class);
      if (wrapper != null && wrapper.getMessageAttributes() != null) {
        return wrapper.getMessageAttributes();
      }
    } catch (IOException e) {
      // Try to unwrap a notification message; if that doesn't work,
      // we're dealing with a message we can't parse. The template or
      // the pipeline potentially knows how to deal with it.
      log.error(
          "Unable to parse message attributes. Unknown message type. (body: {})", messageBody, e);
    }
    return Collections.emptyMap();
  }

  private Id getFailedToBeHandledMetricId(Exception e) {
    return registry
        .createId("echo.pubsub.amazon.failedMessages")
        .withTag("exceptionClass", e.getClass().getSimpleName());
  }
}
