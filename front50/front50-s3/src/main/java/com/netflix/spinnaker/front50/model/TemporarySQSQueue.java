/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.model;

import static net.logstash.logback.argument.StructuredArguments.value;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.policybuilder.iam.IamConditionOperator;
import software.amazon.awssdk.policybuilder.iam.IamEffect;
import software.amazon.awssdk.policybuilder.iam.IamPolicy;
import software.amazon.awssdk.policybuilder.iam.IamPrincipal;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sns.model.Topic;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * Encapsulates the lifecycle of a temporary queue.
 *
 * <p>Upon construction, an SQS queue will be created and subscribed to the specified SNS topic.
 * Upon destruction, both the SQS queue and SNS subscription will be removed.
 */
public class TemporarySQSQueue {
  private final Logger log = LoggerFactory.getLogger(TemporarySQSQueue.class);

  private final SqsClient sqsClient;
  private final SnsClient snsClient;

  private final TemporaryQueue temporaryQueue;

  public TemporarySQSQueue(
      SqsClient sqsClient, SnsClient snsClient, String snsTopicName, String instanceId) {
    this.sqsClient = sqsClient;
    this.snsClient = snsClient;

    String sanitizedInstanceId = getSanitizedInstanceId(instanceId);
    String snsTopicArn = getSnsTopicArn(snsClient, snsTopicName);
    String sqsQueueName = snsTopicName + "__" + sanitizedInstanceId;
    String sqsQueueArn =
        snsTopicArn.substring(0, snsTopicArn.lastIndexOf(":") + 1).replace("sns", "sqs")
            + sqsQueueName;

    this.temporaryQueue = createQueue(snsTopicArn, sqsQueueArn, sqsQueueName);
  }

  List<Message> fetchMessages() {
    ReceiveMessageRequest receiveMessageRequest =
        ReceiveMessageRequest.builder()
            .queueUrl(temporaryQueue.sqsQueueUrl)
            .maxNumberOfMessages(10)
            .waitTimeSeconds(1)
            .build();

    ReceiveMessageResponse response = sqsClient.receiveMessage(receiveMessageRequest);
    return response.messages();
  }

  void markMessageAsHandled(String receiptHandle) {
    try {
      DeleteMessageRequest deleteRequest =
          DeleteMessageRequest.builder()
              .queueUrl(temporaryQueue.sqsQueueUrl)
              .receiptHandle(receiptHandle)
              .build();
      sqsClient.deleteMessage(deleteRequest);
    } catch (SqsException e) {
      log.warn(
          "Error deleting message, reason: {} (receiptHandle: {})", e.getMessage(), receiptHandle);
    }
  }

  @PreDestroy
  void shutdown() {
    try {
      log.debug(
          "Removing Temporary S3 Notification Queue: {}",
          value("queue", temporaryQueue.sqsQueueUrl));
      sqsClient.deleteQueue(
          DeleteQueueRequest.builder().queueUrl(temporaryQueue.sqsQueueUrl).build());
      log.debug(
          "Removed Temporary S3 Notification Queue: {}",
          value("queue", temporaryQueue.sqsQueueUrl));
    } catch (Exception e) {
      log.error(
          "Unable to remove queue: {} (reason: {})",
          value("queue", temporaryQueue.sqsQueueUrl),
          e.getMessage(),
          e);
    }

    try {
      log.debug(
          "Removing S3 Notification Subscription: {}",
          value("topic", temporaryQueue.snsTopicSubscriptionArn));
      snsClient.unsubscribe(
          UnsubscribeRequest.builder()
              .subscriptionArn(temporaryQueue.snsTopicSubscriptionArn)
              .build());
      log.debug(
          "Removed S3 Notification Subscription: {}",
          value("topic", temporaryQueue.snsTopicSubscriptionArn));
    } catch (Exception e) {
      log.error(
          "Unable to unsubscribe queue from topic: {} (reason: {})",
          value("topic", temporaryQueue.snsTopicSubscriptionArn),
          e.getMessage(),
          e);
    }
  }

  private String getSnsTopicArn(SnsClient snsClient, String topicName) {
    return snsClient.listTopicsPaginator(ListTopicsRequest.builder().build()).topics().stream()
        .filter(t -> t.topicArn().toLowerCase().endsWith(":" + topicName.toLowerCase()))
        .map(Topic::topicArn)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException("No SNS topic found (topicName: " + topicName + ")"));
  }

  private TemporaryQueue createQueue(String snsTopicArn, String sqsQueueArn, String sqsQueueName) {
    CreateQueueRequest createQueueRequest =
        CreateQueueRequest.builder()
            .queueName(sqsQueueName)
            .attributes(Collections.singletonMap(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "60"))
            .build();
    CreateQueueResponse createQueueResponse = sqsClient.createQueue(createQueueRequest);
    String sqsQueueUrl = createQueueResponse.queueUrl();
    log.info("Created Temporary S3 Notification Queue: {}", sqsQueueUrl);

    SubscribeRequest subscribeRequest =
        SubscribeRequest.builder()
            .topicArn(snsTopicArn)
            .protocol("sqs")
            .endpoint(sqsQueueArn)
            .build();
    SubscribeResponse subscribeResponse = snsClient.subscribe(subscribeRequest);
    String snsTopicSubscriptionArn = subscribeResponse.subscriptionArn();

    IamPolicy allowSnsPolicy =
        IamPolicy.builder()
            .addStatement(
                s ->
                    s.effect(IamEffect.ALLOW)
                        .addAction("sqs:SendMessage")
                        .addResource(sqsQueueArn)
                        .addPrincipal(IamPrincipal.ALL)
                        .addCondition(
                            c ->
                                c.operator(IamConditionOperator.STRING_EQUALS)
                                    .key("aws:SourceArn")
                                    .value(snsTopicArn)))
            .build();

    Map<QueueAttributeName, String> attributes = new HashMap<>();
    attributes.put(QueueAttributeName.POLICY, allowSnsPolicy.toJson());
    sqsClient.setQueueAttributes(
        SetQueueAttributesRequest.builder().queueUrl(sqsQueueUrl).attributes(attributes).build());

    return new TemporaryQueue(snsTopicArn, sqsQueueArn, sqsQueueUrl, snsTopicSubscriptionArn);
  }

  static String getSanitizedInstanceId(String instanceId) {
    return instanceId.replaceAll("[^\\w\\-]", "_");
  }

  @VisibleForTesting
  TemporaryQueue getTemporaryQueue() {
    return temporaryQueue;
  }

  protected static class TemporaryQueue {
    final String snsTopicArn;
    final String sqsQueueArn;
    final String sqsQueueUrl;
    final String snsTopicSubscriptionArn;

    TemporaryQueue(
        String snsTopicArn,
        String sqsQueueArn,
        String sqsQueueUrl,
        String snsTopicSubscriptionArn) {
      this.snsTopicArn = snsTopicArn;
      this.sqsQueueArn = sqsQueueArn;
      this.sqsQueueUrl = sqsQueueUrl;
      this.snsTopicSubscriptionArn = snsTopicSubscriptionArn;
    }
  }
}
