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
import com.hubspot.jinjava.interpret.FatalTemplateErrorsException;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.artifacts.JinjavaFactory;
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.config.AmazonPubsubProperties;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.utils.NodeIdentity;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.aws.ARN;
import com.netflix.spinnaker.kork.aws.pubsub.PubSubUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * One subscriber for each subscription.
 * The subscriber makes sure the SQS queue is created, subscribes to the SNS topic,
 * polls the queue for messages, and removes them once processed.
 */
public class SQSSubscriber implements Runnable, PubsubSubscriber {

  private static final Logger log = LoggerFactory.getLogger(SQSSubscriber.class);

  private static final int AWS_MAX_NUMBER_OF_MESSAGES = 10;
  static private final PubsubSystem pubsubSystem = PubsubSystem.AMAZON;

  private final ObjectMapper objectMapper;
  private final AmazonSNS amazonSNS;
  private final AmazonSQS amazonSQS;

  private final AmazonPubsubProperties.AmazonPubsubSubscription subscription;

  private final PubsubMessageHandler pubsubMessageHandler;
  private final MessageArtifactTranslator messageArtifactTranslator;

  private final NodeIdentity identity = new NodeIdentity();

  private final Registry registry;

  private final ARN queueARN;
  private final ARN topicARN;

  private String queueId = null;

  private final Supplier<Boolean> isEnabled;

  public SQSSubscriber(ObjectMapper objectMapper,
                       AmazonPubsubProperties.AmazonPubsubSubscription subscription,
                       PubsubMessageHandler pubsubMessageHandler,
                       AmazonSNS amazonSNS,
                       AmazonSQS amazonSQS,
                       Supplier<Boolean> isEnabled,
                       Registry registry,
                       JinjavaFactory jinjavaFactory) {
    this.objectMapper = objectMapper;
    this.subscription = subscription;
    this.pubsubMessageHandler = pubsubMessageHandler;
    this.amazonSNS = amazonSNS;
    this.amazonSQS = amazonSQS;
    this.isEnabled = isEnabled;
    this.registry = registry;

    this.messageArtifactTranslator = new MessageArtifactTranslator(subscription.readTemplatePath(), jinjavaFactory);
    this.queueARN = new ARN(subscription.getQueueARN());
    this.topicARN = new ARN(subscription.getTopicARN());
  }

  public String getWorkerName() {
    return queueARN.getArn() + "/" + SQSSubscriber.class.getSimpleName();
  }

  @Override
  public PubsubSystem pubsubSystem() {
    return pubsubSystem;
  }

  @Override
  public String subscriptionName() {
    return subscription.getName();
  }

  @Override
  public String getName() {
    return subscriptionName();
  }

  @Override
  public void run() {
    log.info("Starting " + getWorkerName());
    initializeQueue();

    while (true) {
      try {
        listenForMessages();
      } catch (QueueDoesNotExistException e){
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
    this.queueId = PubSubUtils.ensureQueueExists(
      amazonSQS, queueARN, topicARN, subscription.getSqsMessageRetentionPeriodSeconds()
    );
    PubSubUtils.subscribeToTopic(amazonSNS, topicARN, queueARN);
  }

  private void listenForMessages() {
    while (isEnabled.get()) {
      ReceiveMessageResult receiveMessageResult = amazonSQS.receiveMessage(
        new ReceiveMessageRequest(queueId)
          .withMaxNumberOfMessages(AWS_MAX_NUMBER_OF_MESSAGES)
          .withVisibilityTimeout(subscription.getVisibilityTimeout())
          .withWaitTimeSeconds(subscription.getWaitTimeSeconds())
          .withMessageAttributeNames("All")
      );

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
      String messagePayload = unmarshallMessageBody(message.getBody());

      Map<String, String> stringifiedMessageAttributes = message.getMessageAttributes().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));

      MessageDescription description = MessageDescription.builder()
        .subscriptionName(subscriptionName())
        .messagePayload(messagePayload)
        .messageAttributes(stringifiedMessageAttributes)
        .pubsubSystem(pubsubSystem)
        .ackDeadlineMillis(TimeUnit.SECONDS.toMillis(50)) // Set a high upper bound on message processing time.
        .retentionDeadlineMillis(TimeUnit.DAYS.toMillis(7)) // Expire key after max retention time, which is 7 days.
        .build();

      AmazonMessageAcknowledger acknowledger = new AmazonMessageAcknowledger(amazonSQS, queueId, message, registry, getName());

      if (subscription.getMessageFormat() != AmazonPubsubProperties.MessageFormat.NONE) {
        try {
          description.setArtifacts(parseArtifacts(description.getMessagePayload(), messageId));
        } catch (FatalTemplateErrorsException e) {
          log.error("Template failed to process artifacts for message {}", message, e);
        }
      }
      pubsubMessageHandler.handleMessage(description, acknowledger, identity.getIdentity(), messageId);
    } catch (Exception e) {
      log.error("Message {} from queue {} failed to be handled", message, queueId, e);
      // Todo emjburns: add dead-letter queue policy
    }
  }

  private List<Artifact> parseArtifacts(String messagePayload, String messageId){
    List<Artifact> artifacts = messageArtifactTranslator.parseArtifacts(messagePayload);
    // Artifact must have at least a reference defined.
    if (artifacts == null || artifacts.size() == 0
        || artifacts.get(0).getReference() == null || artifacts.get(0).getReference().equals("")) {
      return Collections.emptyList();
    }
    return artifacts;
  }

  private String unmarshallMessageBody(String messageBody) {
    String messagePayload = messageBody;
    try {
      NotificationMessageWrapper wrapper = objectMapper.readValue(messagePayload, NotificationMessageWrapper.class);
      if (wrapper != null && wrapper.getMessage() != null) {
        messagePayload = wrapper.getMessage();
      }
    } catch (IOException e) {
      // Try to unwrap a notification message; if that doesn't work,
      // we're dealing with a message we can't parse. The template or
      // the pipeline potentially knows how to deal with it.
      log.error("Unable unmarshal NotificationMessageWrapper. Unknown message type. (body: {})", messageBody, e);
    }
    return messagePayload;
  }
}
