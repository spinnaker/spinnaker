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

package com.netflix.spinnaker.clouddriver.aws.lifecycle;

import com.amazonaws.auth.policy.Condition;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Principal;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.actions.SNSActions;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.SetTopicAttributesRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiptHandleIsInvalidException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.RunnableAgent;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.tags.EntityTagger;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An Agent that subscribes to a particular SQS queue and tags any server groups that had launch
 * errors.
 */
class LaunchFailureNotificationAgent implements RunnableAgent, CustomScheduledAgent {
  private static final Logger log = LoggerFactory.getLogger(LaunchFailureNotificationAgent.class);

  private static final String SUPPORTED_LIFECYCLE_TRANSITION =
      "autoscaling:EC2_INSTANCE_LAUNCH_ERROR";
  private static final int AWS_MAX_NUMBER_OF_MESSAGES = 10;

  private final ObjectMapper objectMapper;
  private final AmazonClientProvider amazonClientProvider;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final LaunchFailureConfigurationProperties properties;
  private final EntityTagger serverGroupTagger;

  private final ARN topicARN;
  private final ARN queueARN;

  private String topicId = null; // the ARN for the topic
  private String queueId = null; // the URL for the queue

  LaunchFailureNotificationAgent(
      ObjectMapper objectMapper,
      AmazonClientProvider amazonClientProvider,
      AccountCredentialsProvider accountCredentialsProvider,
      LaunchFailureConfigurationProperties properties,
      EntityTagger serverGroupTagger) {
    this.objectMapper = objectMapper;
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.properties = properties;
    this.serverGroupTagger = serverGroupTagger;

    Set<? extends AccountCredentials> accountCredentials = accountCredentialsProvider.getAll();
    this.topicARN = new ARN(accountCredentials, properties.getTopicARN());
    this.queueARN = new ARN(accountCredentials, properties.getQueueARN());
  }

  @Override
  public String getAgentType() {
    return queueARN.account.getName()
        + "/"
        + queueARN.region
        + "/"
        + LaunchFailureNotificationAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return AwsProvider.PROVIDER_NAME;
  }

  @Override
  public long getPollIntervalMillis() {
    return TimeUnit.MINUTES.toMillis(1);
  }

  @Override
  public long getTimeoutMillis() {
    return -1;
  }

  @Override
  public void run() {
    List<String> allAccountIds =
        accountCredentialsProvider.getAll().stream()
            .filter(c -> c instanceof NetflixAmazonCredentials)
            .map(AccountCredentials::getAccountId)
            .collect(Collectors.toList());

    AmazonSQS amazonSQS = amazonClientProvider.getAmazonSQS(queueARN.account, queueARN.region);
    this.queueId = ensureQueueExists(amazonSQS, queueARN, topicARN);

    AmazonSNS amazonSNS = amazonClientProvider.getAmazonSNS(topicARN.account, topicARN.region);
    this.topicId = ensureTopicExists(amazonSNS, topicARN, allAccountIds, queueARN);

    AtomicInteger messagesProcessed = new AtomicInteger(0);
    while (messagesProcessed.get() < properties.getMaxMessagesPerCycle()) {
      ReceiveMessageResult receiveMessageResult =
          amazonSQS.receiveMessage(
              new ReceiveMessageRequest(queueId)
                  .withMaxNumberOfMessages(AWS_MAX_NUMBER_OF_MESSAGES)
                  .withVisibilityTimeout(properties.getVisibilityTimeout())
                  .withWaitTimeSeconds(properties.getWaitTimeSeconds()));

      receiveMessageResult
          .getMessages()
          .forEach(
              message -> {
                try {
                  NotificationMessageWrapper notificationMessageWrapper =
                      objectMapper.readValue(message.getBody(), NotificationMessageWrapper.class);

                  NotificationMessage notificationMessage =
                      objectMapper.readValue(
                          notificationMessageWrapper.message, NotificationMessage.class);

                  if (SUPPORTED_LIFECYCLE_TRANSITION.equalsIgnoreCase(notificationMessage.event)) {
                    handleMessage(serverGroupTagger, notificationMessage);
                  }
                } catch (IOException e) {
                  log.error(
                      "Unable to convert NotificationMessage (body: {})", message.getBody(), e);
                }

                deleteMessage(amazonSQS, queueId, message);
                messagesProcessed.incrementAndGet();
              });

      if (receiveMessageResult.getMessages().isEmpty()) {
        // no messages received, stop polling.
        break;
      }
    }

    log.info("Processed {} messages (queueARN: {})", messagesProcessed.get(), queueARN.arn);
  }

  private static void handleMessage(
      EntityTagger serverGroupTagger, NotificationMessage notificationMessage) {
    log.info(
        "Failed to launch instance (asgName: {}, reason: {})",
        notificationMessage.autoScalingGroupName,
        notificationMessage.statusMessage);

    Matcher sqsMatcher = ARN.PATTERN.matcher(notificationMessage.autoScalingGroupARN);
    if (!sqsMatcher.matches()) {
      throw new IllegalArgumentException(
          notificationMessage.autoScalingGroupARN + " is not a valid ARN");
    }

    String region = sqsMatcher.group(1);
    String accountId = sqsMatcher.group(2);

    serverGroupTagger.alert(
        AmazonCloudProvider.ID,
        accountId,
        region,
        null, // no category
        EntityTagger.ENTITY_TYPE_SERVER_GROUP,
        notificationMessage.autoScalingGroupName,
        notificationMessage.event,
        notificationMessage.statusMessage,
        null // no last modified timestamp
        );
  }

  /**
   * Ensure that the topic exists and has a policy granting all accounts permission to publish
   * messages to it
   */
  private static String ensureTopicExists(
      AmazonSNS amazonSNS, ARN topicARN, List<String> allAccountIds, ARN queueARN) {
    topicARN.arn = amazonSNS.createTopic(topicARN.name).getTopicArn();

    amazonSNS.setTopicAttributes(
        new SetTopicAttributesRequest()
            .withTopicArn(topicARN.arn)
            .withAttributeName("Policy")
            .withAttributeValue(buildSNSPolicy(topicARN, allAccountIds).toJson()));

    amazonSNS.subscribe(topicARN.arn, "sqs", queueARN.arn);

    return topicARN.arn;
  }

  /**
   * Ensure that the queue exists and has a policy granting the source topic permission to send
   * messages to it
   */
  private static String ensureQueueExists(AmazonSQS amazonSQS, ARN queueARN, ARN topicARN) {
    String queueUrl;

    try {
      queueUrl = amazonSQS.getQueueUrl(queueARN.name).getQueueUrl();
    } catch (Exception e) {
      queueUrl = amazonSQS.createQueue(queueARN.name).getQueueUrl();
    }

    amazonSQS.setQueueAttributes(
        queueUrl, Collections.singletonMap("Policy", buildSQSPolicy(queueARN, topicARN).toJson()));

    return queueUrl;
  }

  private static Policy buildSNSPolicy(ARN topicARN, List<String> allAccountIds) {
    Statement statement = new Statement(Statement.Effect.Allow).withActions(SNSActions.Publish);
    statement.setPrincipals(
        allAccountIds.stream().map(Principal::new).collect(Collectors.toList()));
    statement.setResources(Collections.singletonList(new Resource(topicARN.arn)));

    return new Policy("allow-remote-account-send", Collections.singletonList(statement));
  }

  private static Policy buildSQSPolicy(ARN queue, ARN topic) {
    Statement statement = new Statement(Statement.Effect.Allow).withActions(SQSActions.SendMessage);
    statement.setPrincipals(Principal.All);
    statement.setResources(Collections.singletonList(new Resource(queue.arn)));
    statement.setConditions(
        Collections.singletonList(
            new Condition()
                .withType("ArnEquals")
                .withConditionKey("aws:SourceArn")
                .withValues(topic.arn)));

    return new Policy("allow-sns-topic-send", Collections.singletonList(statement));
  }

  private static void deleteMessage(AmazonSQS amazonSQS, String queueUrl, Message message) {
    try {
      amazonSQS.deleteMessage(queueUrl, message.getReceiptHandle());
    } catch (ReceiptHandleIsInvalidException e) {
      log.warn(
          "Error deleting lifecycle message, reason: {} (receiptHandle: {})",
          e.getMessage(),
          message.getReceiptHandle());
    }
  }
}
