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

import com.amazonaws.auth.policy.Condition;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Principal;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.ListTopicsResult;
import com.amazonaws.services.sns.model.Topic;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiptHandleIsInvalidException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates the lifecycle of a temporary queue.
 *
 * <p>Upon construction, an SQS queue will be created and subscribed to the specified SNS topic.
 * Upon destruction, both the SQS queue and SNS subscription will be removed.
 */
public class TemporarySQSQueue {
  private final Logger log = LoggerFactory.getLogger(TemporarySQSQueue.class);

  private final AmazonSQS amazonSQS;
  private final AmazonSNS amazonSNS;

  private final TemporaryQueue temporaryQueue;

  public TemporarySQSQueue(
      AmazonSQS amazonSQS, AmazonSNS amazonSNS, String snsTopicName, String instanceId) {
    this.amazonSQS = amazonSQS;
    this.amazonSNS = amazonSNS;

    String sanitizedInstanceId = getSanitizedInstanceId(instanceId);
    String snsTopicArn = getSnsTopicArn(amazonSNS, snsTopicName);
    String sqsQueueName = snsTopicName + "__" + sanitizedInstanceId;
    String sqsQueueArn =
        snsTopicArn.substring(0, snsTopicArn.lastIndexOf(":") + 1).replace("sns", "sqs")
            + sqsQueueName;

    this.temporaryQueue = createQueue(snsTopicArn, sqsQueueArn, sqsQueueName);
  }

  List<Message> fetchMessages() {
    ReceiveMessageResult receiveMessageResult =
        amazonSQS.receiveMessage(
            new ReceiveMessageRequest(temporaryQueue.sqsQueueUrl)
                .withMaxNumberOfMessages(10)
                .withWaitTimeSeconds(1));

    return receiveMessageResult.getMessages();
  }

  void markMessageAsHandled(String receiptHandle) {
    try {
      amazonSQS.deleteMessage(temporaryQueue.sqsQueueUrl, receiptHandle);
    } catch (ReceiptHandleIsInvalidException e) {
      log.warn(
          "Error deleting message, reason: {} (receiptHandle: {})",
          e.getMessage(),
          value("receiptHandle", receiptHandle));
    }
  }

  @PreDestroy
  void shutdown() {
    try {
      log.debug(
          "Removing Temporary S3 Notification Queue: {}",
          value("queue", temporaryQueue.sqsQueueUrl));
      amazonSQS.deleteQueue(temporaryQueue.sqsQueueUrl);
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
          "Removing S3 Notification Subscription: {}", temporaryQueue.snsTopicSubscriptionArn);
      amazonSNS.unsubscribe(temporaryQueue.snsTopicSubscriptionArn);
      log.debug("Removed S3 Notification Subscription: {}", temporaryQueue.snsTopicSubscriptionArn);
    } catch (Exception e) {
      log.error(
          "Unable to unsubscribe queue from topic: {} (reason: {})",
          value("topic", temporaryQueue.snsTopicSubscriptionArn),
          e.getMessage(),
          e);
    }
  }

  private String getSnsTopicArn(AmazonSNS amazonSNS, String topicName) {
    ListTopicsResult listTopicsResult = amazonSNS.listTopics();
    String nextToken = listTopicsResult.getNextToken();
    List<Topic> topics = listTopicsResult.getTopics();

    while (nextToken != null) {
      listTopicsResult = amazonSNS.listTopics(nextToken);
      nextToken = listTopicsResult.getNextToken();
      topics.addAll(listTopicsResult.getTopics());
    }

    return topics.stream()
        .filter(t -> t.getTopicArn().toLowerCase().endsWith(":" + topicName.toLowerCase()))
        .map(Topic::getTopicArn)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException("No SNS topic found (topicName: " + topicName + ")"));
  }

  private TemporaryQueue createQueue(String snsTopicArn, String sqsQueueArn, String sqsQueueName) {
    String sqsQueueUrl =
        amazonSQS
            .createQueue(
                new CreateQueueRequest()
                    .withQueueName(sqsQueueName)
                    .withAttributes(
                        Collections.singletonMap(
                            "MessageRetentionPeriod", "60")) // 60s message retention
                )
            .getQueueUrl();
    log.info("Created Temporary S3 Notification Queue: {}", value("queue", sqsQueueUrl));

    String snsTopicSubscriptionArn =
        amazonSNS.subscribe(snsTopicArn, "sqs", sqsQueueArn).getSubscriptionArn();

    Statement snsStatement =
        new Statement(Statement.Effect.Allow).withActions(SQSActions.SendMessage);
    snsStatement.setPrincipals(Principal.All);
    snsStatement.setResources(Collections.singletonList(new Resource(sqsQueueArn)));
    snsStatement.setConditions(
        Collections.singletonList(
            new Condition()
                .withType("ArnEquals")
                .withConditionKey("aws:SourceArn")
                .withValues(snsTopicArn)));

    Policy allowSnsPolicy = new Policy("allow-sns", Collections.singletonList(snsStatement));

    HashMap<String, String> attributes = new HashMap<>();
    attributes.put("Policy", allowSnsPolicy.toJson());
    amazonSQS.setQueueAttributes(sqsQueueUrl, attributes);

    return new TemporaryQueue(snsTopicArn, sqsQueueArn, sqsQueueUrl, snsTopicSubscriptionArn);
  }

  static String getSanitizedInstanceId(String instanceId) {
    return instanceId.replaceAll("[^\\w\\-]", "_");
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
