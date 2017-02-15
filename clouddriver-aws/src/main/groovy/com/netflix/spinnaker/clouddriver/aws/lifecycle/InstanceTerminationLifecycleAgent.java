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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableInstanceDiscoveryDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.AwsEurekaSupport;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport.DiscoveryStatus;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class InstanceTerminationLifecycleAgent implements RunnableAgent, CustomScheduledAgent {

  private static final Logger log = LoggerFactory.getLogger(InstanceTerminationLifecycleAgent.class);

  private static final int AWS_MAX_NUMBER_OF_MESSAGES = 10;
  private static final String SUPPORTED_LIFECYCLE_TRANSITION = "autoscaling:EC2_INSTANCE_TERMINATING";

  ObjectMapper objectMapper;
  AmazonClientProvider amazonClientProvider;
  AccountCredentialsProvider accountCredentialsProvider;
  InstanceTerminationConfigurationProperties properties;
  Provider<AwsEurekaSupport> discoverySupport;

  private final ARN queueARN;
  private final ARN topicARN;

  private String queueId = null;

  public InstanceTerminationLifecycleAgent(ObjectMapper objectMapper,
                                           AmazonClientProvider amazonClientProvider,
                                           AccountCredentialsProvider accountCredentialsProvider,
                                           InstanceTerminationConfigurationProperties properties,
                                           Provider<AwsEurekaSupport> discoverySupport) {
    this.objectMapper = objectMapper;
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.properties = properties;
    this.discoverySupport = discoverySupport;

    Set<? extends AccountCredentials> accountCredentials = accountCredentialsProvider.getAll();
    this.queueARN = new ARN(accountCredentials, properties.getQueueARN());
    this.topicARN = new ARN(accountCredentials, properties.getTopicARN());
  }

  @Override
  public String getAgentType() {
    return queueARN.account.getName() + "/" + queueARN.region + "/" + InstanceTerminationLifecycleAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return AwsProvider.PROVIDER_NAME;
  }

  @Override
  public long getPollIntervalMillis() {
    return TimeUnit.SECONDS.toMillis(30);
  }

  @Override
  public long getTimeoutMillis() {
    return -1;
  }

  @Override
  public void run() {
    AmazonSQS amazonSQS = amazonClientProvider.getAmazonSQS(queueARN.account, queueARN.region);
    AmazonSNS amazonSNS = amazonClientProvider.getAmazonSNS(topicARN.account, topicARN.region);

    List<String> allAccountIds = accountCredentialsProvider.getAll()
      .stream()
      .map(AccountCredentials::getAccountId)
      .filter(a -> a != null)
      .collect(Collectors.toList());

    this.queueId = ensureQueueExists(amazonSQS, queueARN, topicARN);
    ensureTopicExists(amazonSNS, topicARN, allAccountIds, queueARN);

    AtomicInteger messagesProcessed = new AtomicInteger(0);
    AtomicInteger messagesSkipped = new AtomicInteger(0);
    while (messagesProcessed.get() < properties.getMaxMessagesPerCycle()) {
      ReceiveMessageResult receiveMessageResult = amazonSQS.receiveMessage(
        new ReceiveMessageRequest(queueId)
          .withMaxNumberOfMessages(AWS_MAX_NUMBER_OF_MESSAGES)
          .withVisibilityTimeout(properties.getVisibilityTimeout())
          .withWaitTimeSeconds(properties.getWaitTimeSeconds())
      );

      if (receiveMessageResult.getMessages().isEmpty()) {
        // No messages
        break;
      }

      receiveMessageResult.getMessages().forEach(message -> {
        LifecycleMessage lifecycleMessage = unmarshalLifecycleMessage(message.getBody());

        if (lifecycleMessage != null) {
          if (!SUPPORTED_LIFECYCLE_TRANSITION.equalsIgnoreCase(lifecycleMessage.lifecycleTransition)) {
            log.info("Ignoring unsupported lifecycle transition: " + lifecycleMessage.lifecycleTransition);
            messagesSkipped.incrementAndGet();
            return;
          }

          Task originalTask = TaskRepository.threadLocalTask.get();
          try {
            TaskRepository.threadLocalTask.set(
              Optional.ofNullable(originalTask).orElse(new DefaultTask(InstanceTerminationLifecycleAgent.class.getSimpleName()))
            );
            handleMessage(lifecycleMessage, TaskRepository.threadLocalTask.get());
          } finally {
            TaskRepository.threadLocalTask.set(originalTask);
          }
        } else {
          messagesSkipped.incrementAndGet();
        }

        deleteMessage(amazonSQS, queueId, message);
        messagesProcessed.incrementAndGet();
      });
    }

    log.info("Processed {} messages, {} skipped (queueARN: {})", messagesProcessed.get(), messagesSkipped.get(), queueARN.arn);
  }

  private LifecycleMessage unmarshalLifecycleMessage(String messageBody) {
    String body = messageBody;
    try {
      NotificationMessageWrapper wrapper = objectMapper.readValue(messageBody, NotificationMessageWrapper.class);
      if (wrapper != null && wrapper.message != null) {
        body = wrapper.message;
      }
    } catch (IOException e) {
      // Try to unwrap a notification message; if that doesn't work,
      // assume that we're dealing with a message directly from SQS.
      log.debug("Unable unmarshal NotificationMessageWrapper. Assuming SQS message. (body: {})", messageBody, e);
    }

    LifecycleMessage lifecycleMessage = null;
    try {
      lifecycleMessage = objectMapper.readValue(body, LifecycleMessage.class);
    } catch (IOException e) {
      log.error("Unable to unmarshal LifecycleMessage (body: {})", body, e);
    }

    return lifecycleMessage;
  }

  private void handleMessage(LifecycleMessage message, Task task) {
    List<String> instanceIds = Collections.singletonList(message.ec2InstanceId);

    EnableDisableInstanceDiscoveryDescription description = new EnableDisableInstanceDiscoveryDescription();
    description.setCredentials(getAccountCredentialsById(message.accountId));
    description.setRegion(queueARN.region);
    description.setAsgName(message.autoScalingGroupName);
    description.setInstanceIds(instanceIds);

    discoverySupport.get().updateDiscoveryStatusForInstances(
      description, task, "handleLifecycleMessage", DiscoveryStatus.Disable, instanceIds
    );
  }

  private static void deleteMessage(AmazonSQS amazonSQS, String queueUrl, Message message) {
    try {
      amazonSQS.deleteMessage(queueUrl, message.getReceiptHandle());
    } catch (ReceiptHandleIsInvalidException e) {
      log.warn("Error deleting lifecycle message, reason: {} (receiptHandle: {})", e.getMessage(), message.getReceiptHandle());
    }
  }

  private NetflixAmazonCredentials getAccountCredentialsById(String accountId) {
    return (NetflixAmazonCredentials) accountCredentialsProvider.getAll()
      .stream()
      .filter(c -> c.getAccountId().equals(accountId))
      .findFirst()
      .orElseThrow((Supplier<RuntimeException>) () -> {
        return new RuntimeException(String.format("Unable to find AmazonCredentials by id (id: %s)", accountId));
      });
  }

  private static String ensureTopicExists(AmazonSNS amazonSNS,
                                          ARN topicARN,
                                          List<String> allAccountIds,
                                          ARN queueARN) {
    topicARN.arn = amazonSNS.createTopic(topicARN.name).getTopicArn();

    amazonSNS.setTopicAttributes(
      new SetTopicAttributesRequest()
        .withTopicArn(topicARN.arn)
        .withAttributeName("Policy")
        .withAttributeValue(buildSNSPolicy(topicARN, allAccountIds).toJson())
    );

    amazonSNS.subscribe(topicARN.arn, "sqs", queueARN.arn);

    return topicARN.arn;
  }

  private static Policy buildSNSPolicy(ARN topicARN, List<String> allAccountIds) {
    Statement statement = new Statement(Statement.Effect.Allow).withActions(SNSActions.Publish);
    statement.setPrincipals(allAccountIds.stream().map(Principal::new).collect(Collectors.toList()));
    statement.setResources(Collections.singletonList(new Resource(topicARN.arn)));

    return new Policy("allow-remote-account-send", Collections.singletonList(statement));
  }

  private static String ensureQueueExists(AmazonSQS amazonSQS, ARN queueARN, ARN topicARN) {
    String queueUrl = amazonSQS.createQueue(queueARN.name).getQueueUrl();
    amazonSQS.setQueueAttributes(
      queueUrl, Collections.singletonMap("Policy", buildSQSPolicy(queueARN, topicARN).toJson())
    );

    return queueUrl;
  }

  private static Policy buildSQSPolicy(ARN queue, ARN topic) {
    Statement statement = new Statement(Statement.Effect.Allow).withActions(SQSActions.SendMessage);
    statement.setPrincipals(Principal.All);
    statement.setResources(Collections.singletonList(new Resource(queue.arn)));
    statement.setConditions(Collections.singletonList(
      new Condition().withType("ArnEquals").withConditionKey("aws:SourceArn").withValues(topic.arn)
    ));

    return new Policy("allow-sns-topic-send", Collections.singletonList(statement));
  }
}
