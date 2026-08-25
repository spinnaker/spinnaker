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
import com.netflix.frigga.Names;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.AwsEurekaSupport;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials.LifecycleHook;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.eureka.api.Eureka;
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport.DiscoveryStatus;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import jakarta.inject.Provider;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiptHandleIsInvalidException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

public class InstanceTerminationLifecycleWorker implements Runnable {

  private static final Logger log =
      LoggerFactory.getLogger(InstanceTerminationLifecycleWorker.class);

  private static final int AWS_MAX_NUMBER_OF_MESSAGES = 10;
  private static final String SUPPORTED_LIFECYCLE_TRANSITION =
      "autoscaling:EC2_INSTANCE_TERMINATING";

  ObjectMapper objectMapper;
  AmazonClientProvider amazonClientProvider;
  CredentialsRepository<NetflixAmazonCredentials> credentialsRepository;
  InstanceTerminationConfigurationProperties properties;
  Provider<AwsEurekaSupport> discoverySupport;
  Registry registry;

  private final ARN queueARN;
  private final ARN topicARN;

  private String queueId = null;

  public InstanceTerminationLifecycleWorker(
      ObjectMapper objectMapper,
      AmazonClientProvider amazonClientProvider,
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
      InstanceTerminationConfigurationProperties properties,
      Provider<AwsEurekaSupport> discoverySupport,
      Registry registry) {
    this.objectMapper = objectMapper;
    this.amazonClientProvider = amazonClientProvider;
    this.credentialsRepository = credentialsRepository;
    this.properties = properties;
    this.discoverySupport = discoverySupport;
    this.registry = registry;

    Set<NetflixAmazonCredentials> accountCredentials = credentialsRepository.getAll();
    this.queueARN = new ARN(accountCredentials, properties.getQueueARN());
    this.topicARN = new ARN(accountCredentials, properties.getTopicARN());
  }

  public String getWorkerName() {
    return queueARN.account.getName()
        + "/"
        + queueARN.region
        + "/"
        + InstanceTerminationLifecycleWorker.class.getSimpleName();
  }

  @Override
  public void run() {
    log.info("Starting " + getWorkerName());

    while (true) {
      try {
        listenForMessages();
      } catch (Throwable e) {
        log.error("Unexpected error running " + getWorkerName() + ", restarting", e);
      }
    }
  }

  private void listenForMessages() {
    SqsClient sqsClient = amazonClientProvider.getAmazonSqsV2(queueARN.account, queueARN.region);
    SnsClient snsClient = amazonClientProvider.getAmazonSnsV2(topicARN.account, topicARN.region);

    Set<? extends AccountCredentials> accountCredentials = credentialsRepository.getAll();
    List<String> allAccountIds = getAllAccountIds(accountCredentials);

    this.queueId =
        ensureQueueExists(
            sqsClient,
            queueARN,
            topicARN,
            getSourceRoleArns(accountCredentials),
            properties.getSqsMessageRetentionPeriodSeconds());
    ensureTopicExists(snsClient, topicARN, allAccountIds, queueARN);

    while (true) {
      ReceiveMessageResponse receiveMessageResponse =
          sqsClient.receiveMessage(
              ReceiveMessageRequest.builder()
                  .queueUrl(queueId)
                  .maxNumberOfMessages(AWS_MAX_NUMBER_OF_MESSAGES)
                  .visibilityTimeout(properties.getVisibilityTimeout())
                  .waitTimeSeconds(properties.getWaitTimeSeconds())
                  .build());

      if (receiveMessageResponse.messages().isEmpty()) {
        // No messages
        continue;
      }

      receiveMessageResponse
          .messages()
          .forEach(
              message -> {
                LifecycleMessage lifecycleMessage = unmarshalLifecycleMessage(message.body());

                if (lifecycleMessage != null) {
                  if (!SUPPORTED_LIFECYCLE_TRANSITION.equalsIgnoreCase(
                      lifecycleMessage.lifecycleTransition)) {
                    log.info(
                        "Ignoring unsupported lifecycle transition: "
                            + lifecycleMessage.lifecycleTransition);
                    deleteMessage(sqsClient, queueId, message);
                    return;
                  }
                  handleMessage(lifecycleMessage);
                }

                deleteMessage(sqsClient, queueId, message);
                registry.counter(getProcessedMetricId(queueARN.region)).increment();
              });
    }
  }

  LifecycleMessage unmarshalLifecycleMessage(String messageBody) {
    String body = messageBody;
    try {
      NotificationMessageWrapper wrapper =
          objectMapper.readValue(messageBody, NotificationMessageWrapper.class);
      if (wrapper != null && wrapper.message != null) {
        body = wrapper.message;
      }
    } catch (IOException e) {
      // Try to unwrap a notification message; if that doesn't work,
      // assume that we're dealing with a message directly from SQS.
      log.debug(
          "Unable unmarshal NotificationMessageWrapper. Assuming SQS message. (body: {})",
          messageBody,
          e);
    }

    LifecycleMessage lifecycleMessage = null;
    try {
      lifecycleMessage = objectMapper.readValue(body, LifecycleMessage.class);
    } catch (IOException e) {
      log.error("Unable to unmarshal LifecycleMessage (body: {})", body, e);
    }

    return lifecycleMessage;
  }

  void handleMessage(LifecycleMessage message) {
    NetflixAmazonCredentials credentials = getAccountCredentialsById(message.accountId);
    if (credentials == null) {
      log.error("Unable to find credentials for account id: {}", message.accountId);
      return;
    }

    Names names = Names.parseName(message.autoScalingGroupName);
    Eureka eureka = discoverySupport.get().getEureka(credentials, queueARN.region);

    if (!updateInstanceStatus(eureka, names.getApp(), message.ec2InstanceId)) {
      registry.counter(getFailedMetricId(queueARN.region)).increment();
    }
    recordLag(
        message.time,
        queueARN.region,
        message.accountId,
        message.autoScalingGroupName,
        message.ec2InstanceId);
  }

  private boolean updateInstanceStatus(Eureka eureka, String app, String instanceId) {
    int retry = 0;
    while (retry < properties.getEurekaUpdateStatusRetryMax()) {
      retry++;
      try {
        eureka.updateInstanceStatus(app, instanceId, DiscoveryStatus.OUT_OF_SERVICE.getValue());
        return true;
      } catch (SpinnakerServerException e) {
        final String recoverableMessage =
            "Failed marking app out of service (status: {}, app: {}, instance: {}, retry: {})";
        if (e instanceof SpinnakerHttpException
            && HttpStatus.NOT_FOUND.value() == ((SpinnakerHttpException) e).getResponseCode()) {
          log.warn(recoverableMessage, 404, app, instanceId, retry);
        } else if (e instanceof SpinnakerNetworkException) {
          log.error(recoverableMessage, "none", app, instanceId, retry, e);
        } else {
          log.error(
              "Irrecoverable error while marking app out of service (app: {}, instance: {}, retry: {})",
              app,
              instanceId,
              retry,
              e);
          break;
        }
      }
    }

    return false;
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

  private NetflixAmazonCredentials getAccountCredentialsById(String accountId) {
    for (NetflixAmazonCredentials credentials : credentialsRepository.getAll()) {
      if (credentials.getAccountId() != null && credentials.getAccountId().equals(accountId)) {
        return credentials;
      }
    }
    return null;
  }

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

  static String ensureQueueExists(
      SqsClient sqsClient,
      ARN queueARN,
      ARN topicARN,
      Set<String> terminatingRoleArns,
      int sqsMessageRetentionPeriodSeconds) {
    String queueUrl =
        sqsClient
            .createQueue(CreateQueueRequest.builder().queueName(queueARN.name).build())
            .queueUrl();

    Map<QueueAttributeName, String> attributes = new HashMap<>();
    attributes.put(
        QueueAttributeName.POLICY,
        buildSQSPolicy(queueARN, topicARN, terminatingRoleArns).toJson());
    attributes.put(
        QueueAttributeName.MESSAGE_RETENTION_PERIOD,
        Integer.toString(sqsMessageRetentionPeriodSeconds));
    sqsClient.setQueueAttributes(
        SetQueueAttributesRequest.builder().queueUrl(queueUrl).attributes(attributes).build());

    return queueUrl;
  }

  /**
   * This policy allows operators to choose whether or not to have lifecycle hooks to be sent via
   * SNS for fanout, or be sent directly to an SQS queue from the autoscaling group.
   */
  static IamPolicy buildSQSPolicy(ARN queue, ARN topic, Set<String> terminatingRoleArns) {
    IamCondition condition =
        IamCondition.builder().operator("ArnEquals").key("aws:SourceArn").value(topic.arn).build();
    IamStatement snsStatement =
        IamStatement.builder()
            .effect(IamEffect.ALLOW)
            .actions(List.of(IamAction.create("sqs:SendMessage")))
            .principals(List.of(IamPrincipal.ALL))
            .resources(List.of(IamResource.create(queue.arn)))
            .conditions(List.of(condition))
            .build();

    List<IamPrincipal> rolePrincipals =
        terminatingRoleArns.stream()
            .map(arn -> IamPrincipal.create(IamPrincipalType.AWS, arn))
            .collect(Collectors.toList());
    IamStatement sqsStatement =
        IamStatement.builder()
            .effect(IamEffect.ALLOW)
            .actions(
                List.of(IamAction.create("sqs:SendMessage"), IamAction.create("sqs:GetQueueUrl")))
            .principals(rolePrincipals)
            .resources(List.of(IamResource.create(queue.arn)))
            .build();

    return IamPolicy.builder()
        .id("allow-sns-or-sqs-send")
        .statements(Arrays.asList(snsStatement, sqsStatement))
        .build();
  }

  Id getLagMetricId(String region) {
    return registry.createId("terminationLifecycle.lag", "region", region);
  }

  void recordLag(Date start, String region, String account, String serverGroup, String instanceId) {
    if (start != null) {
      Long lag = registry.clock().wallTime() - start.getTime();
      log.info(
          "Lifecycle message processed (account: {}, serverGroup: {}, instance: {}, lagSeconds: {})",
          account,
          serverGroup,
          instanceId,
          Duration.ofMillis(lag).getSeconds());
      registry.gauge(getLagMetricId(region), lag);
    }
  }

  Id getProcessedMetricId(String region) {
    return registry.createId("terminationLifecycle.totalProcessed", "region", region);
  }

  Id getFailedMetricId(String region) {
    return registry.createId("terminationLifecycle.totalFailed", "region", region);
  }

  private static List<String> getAllAccountIds(
      Set<? extends AccountCredentials> accountCredentials) {
    return accountCredentials.stream()
        .map(AccountCredentials::getAccountId)
        .filter(a -> a != null)
        .collect(Collectors.toList());
  }

  private static <T extends AccountCredentials> Set<String> getSourceRoleArns(
      Set<T> allCredentials) {
    Set<String> sourceRoleArns = new HashSet<>();
    for (T credentials : allCredentials) {
      if (credentials instanceof NetflixAmazonCredentials) {
        NetflixAmazonCredentials c = (NetflixAmazonCredentials) credentials;
        if (c.getLifecycleHooks() != null) {
          sourceRoleArns.addAll(
              c.getLifecycleHooks().stream()
                  .filter(
                      h ->
                          "autoscaling:EC2_INSTANCE_TERMINATING".equals(h.getLifecycleTransition()))
                  .map(LifecycleHook::getRoleARN)
                  .collect(Collectors.toSet()));
        }
      }
    }
    return sourceRoleArns;
  }
}
