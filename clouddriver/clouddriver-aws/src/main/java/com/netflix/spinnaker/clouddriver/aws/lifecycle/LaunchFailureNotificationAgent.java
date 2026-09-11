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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.RunnableAgent;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.tags.EntityTagger;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.policybuilder.iam.IamAction;
import software.amazon.awssdk.policybuilder.iam.IamCondition;
import software.amazon.awssdk.policybuilder.iam.IamEffect;
import software.amazon.awssdk.policybuilder.iam.IamPolicy;
import software.amazon.awssdk.policybuilder.iam.IamPrincipal;
import software.amazon.awssdk.policybuilder.iam.IamPrincipalType;
import software.amazon.awssdk.policybuilder.iam.IamResource;
import software.amazon.awssdk.policybuilder.iam.IamStatement;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SetTopicAttributesRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiptHandleIsInvalidException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

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
  private final CredentialsRepository<NetflixAmazonCredentials> credentialsRepository;
  private final LaunchFailureConfigurationProperties properties;
  private final EntityTagger serverGroupTagger;

  private final ARN topicARN;
  private final ARN queueARN;

  private String topicId = null; // the ARN for the topic
  private String queueId = null; // the URL for the queue

  LaunchFailureNotificationAgent(
      ObjectMapper objectMapper,
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials netflixAmazonCredentials,
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
      LaunchFailureConfigurationProperties properties,
      EntityTagger serverGroupTagger) {
    this.objectMapper = objectMapper;
    this.amazonClientProvider = amazonClientProvider;
    this.credentialsRepository = credentialsRepository;
    this.properties = properties;
    this.serverGroupTagger = serverGroupTagger;

    this.topicARN = new ARN(netflixAmazonCredentials, properties.getTopicARN());
    this.queueARN = new ARN(netflixAmazonCredentials, properties.getQueueARN());
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
        credentialsRepository.getAll().stream()
            .map(AccountCredentials::getAccountId)
            .collect(Collectors.toList());

    SqsClient sqsClient = amazonClientProvider.getAmazonSqsV2(queueARN.account, queueARN.region);
    this.queueId = ensureQueueExists(sqsClient, queueARN, topicARN);

    SnsClient snsClient = amazonClientProvider.getAmazonSnsV2(topicARN.account, topicARN.region);
    this.topicId = ensureTopicExists(snsClient, topicARN, allAccountIds, queueARN);

    AtomicInteger messagesProcessed = new AtomicInteger(0);
    while (messagesProcessed.get() < properties.getMaxMessagesPerCycle()) {
      ReceiveMessageResponse receiveMessageResponse =
          sqsClient.receiveMessage(
              ReceiveMessageRequest.builder()
                  .queueUrl(queueId)
                  .maxNumberOfMessages(AWS_MAX_NUMBER_OF_MESSAGES)
                  .visibilityTimeout(properties.getVisibilityTimeout())
                  .waitTimeSeconds(properties.getWaitTimeSeconds())
                  .build());

      receiveMessageResponse
          .messages()
          .forEach(
              message -> {
                try {
                  NotificationMessageWrapper notificationMessageWrapper =
                      objectMapper.readValue(message.body(), NotificationMessageWrapper.class);

                  NotificationMessage notificationMessage =
                      objectMapper.readValue(
                          notificationMessageWrapper.message, NotificationMessage.class);

                  if (SUPPORTED_LIFECYCLE_TRANSITION.equalsIgnoreCase(notificationMessage.event)) {
                    handleMessage(serverGroupTagger, notificationMessage);
                  }
                } catch (IOException e) {
                  log.error("Unable to convert NotificationMessage (body: {})", message.body(), e);
                }

                deleteMessage(sqsClient, queueId, message);
                messagesProcessed.incrementAndGet();
              });

      if (receiveMessageResponse.messages().isEmpty()) {
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
  static String ensureTopicExists(
      SnsClient snsClient, ARN topicARN, List<String> allAccountIds, ARN queueARN) {
    topicARN.arn =
        snsClient.createTopic(CreateTopicRequest.builder().name(topicARN.name).build()).topicArn();

    snsClient.setTopicAttributes(
        SetTopicAttributesRequest.builder()
            .topicArn(topicARN.arn)
            .attributeName("Policy")
            .attributeValue(buildSNSPolicy(topicARN, allAccountIds).toJson())
            .build());

    snsClient.subscribe(
        SubscribeRequest.builder()
            .topicArn(topicARN.arn)
            .protocol("sqs")
            .endpoint(queueARN.arn)
            .build());

    return topicARN.arn;
  }

  /**
   * Ensure that the queue exists and has a policy granting the source topic permission to send
   * messages to it
   */
  static String ensureQueueExists(SqsClient sqsClient, ARN queueARN, ARN topicARN) {
    String queueUrl;

    try {
      queueUrl =
          sqsClient
              .getQueueUrl(GetQueueUrlRequest.builder().queueName(queueARN.name).build())
              .queueUrl();
    } catch (QueueDoesNotExistException e) {
      queueUrl =
          sqsClient
              .createQueue(CreateQueueRequest.builder().queueName(queueARN.name).build())
              .queueUrl();
    }

    sqsClient.setQueueAttributes(
        SetQueueAttributesRequest.builder()
            .queueUrl(queueUrl)
            .attributes(
                Map.of(QueueAttributeName.POLICY, buildSQSPolicy(queueARN, topicARN).toJson()))
            .build());

    return queueUrl;
  }

  static IamPolicy buildSNSPolicy(ARN topicARN, List<String> allAccountIds) {
    List<IamPrincipal> principals =
        allAccountIds.stream()
            .map(id -> IamPrincipal.create(IamPrincipalType.AWS, id))
            .collect(Collectors.toList());
    IamStatement statement =
        IamStatement.builder()
            .effect(IamEffect.ALLOW)
            .actions(List.of(IamAction.create("SNS:Publish")))
            .principals(principals)
            .resources(List.of(IamResource.create(topicARN.arn)))
            .build();

    return IamPolicy.builder().id("allow-remote-account-send").addStatement(statement).build();
  }

  static IamPolicy buildSQSPolicy(ARN queue, ARN topic) {
    IamCondition condition =
        IamCondition.builder().operator("ArnEquals").key("aws:SourceArn").value(topic.arn).build();
    IamStatement statement =
        IamStatement.builder()
            .effect(IamEffect.ALLOW)
            .actions(List.of(IamAction.create("sqs:SendMessage")))
            .principals(List.of(IamPrincipal.ALL))
            .resources(List.of(IamResource.create(queue.arn)))
            .conditions(List.of(condition))
            .build();

    return IamPolicy.builder().id("allow-sns-topic-send").addStatement(statement).build();
  }

  private static void deleteMessage(SqsClient sqsClient, String queueUrl, Message message) {
    try {
      sqsClient.deleteMessage(
          DeleteMessageRequest.builder()
              .queueUrl(queueUrl)
              .receiptHandle(message.receiptHandle())
              .build());
    } catch (ReceiptHandleIsInvalidException e) {
      log.warn(
          "Error deleting lifecycle message, reason: {} (receiptHandle: {})",
          e.getMessage(),
          message.receiptHandle());
    }
  }
}
