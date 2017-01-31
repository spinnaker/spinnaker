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
import com.amazonaws.auth.policy.actions.SQSActions;
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

    Set<String> allAccountIds = accountCredentialsProvider.getAll()
      .stream()
      .map(AccountCredentials::getAccountId)
      .filter(a -> a != null)
      .collect(Collectors.toSet());

    this.queueId = ensureQueueExists(amazonSQS, queueARN, properties.getSourceARN(), allAccountIds);

    AtomicInteger messagesProcessed = new AtomicInteger(0);
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
        try {
          LifecycleMessage lifecycleMessage = objectMapper.readValue(message.getBody(), LifecycleMessage.class);

          if (SUPPORTED_LIFECYCLE_TRANSITION.equalsIgnoreCase(lifecycleMessage.lifecycleTransition)) {
            Task originalTask = TaskRepository.threadLocalTask.get();
            try {
              TaskRepository.threadLocalTask.set(
                Optional.ofNullable(originalTask).orElse(new DefaultTask(InstanceTerminationLifecycleAgent.class.getSimpleName()))
              );
              handleMessage(lifecycleMessage, TaskRepository.threadLocalTask.get());
            } finally {
              TaskRepository.threadLocalTask.set(originalTask);
            }
          }
        } catch (IOException e) {
          log.error("Unable to convert NotificationMessage (body: {})", message.getBody(), e);
        }

        deleteMessage(amazonSQS, queueId, message);
        messagesProcessed.incrementAndGet();
      });
    }

    log.info("Processed {} messages (queueARN: {})", messagesProcessed.get(), queueARN.arn);
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

  private static String ensureQueueExists(AmazonSQS amazonSQS, ARN queueARN, String sourceARN, Set<String> allAccountIds) {
    String queueUrl = amazonSQS.createQueue(queueARN.name).getQueueUrl();
    amazonSQS.setQueueAttributes(
      queueUrl, Collections.singletonMap("Policy", buildSQSPolicy(queueARN, sourceARN, allAccountIds).toJson())
    );

    return queueUrl;
  }

  private static Policy buildSQSPolicy(ARN queue, String sourceARN, Set<String> allAccountIds) {
    Statement statement = new Statement(Statement.Effect.Allow).withActions(SQSActions.SendMessage);
    statement.setPrincipals(allAccountIds.stream().map(Principal::new).collect(Collectors.toList()));
    statement.setResources(Collections.singletonList(new Resource(queue.arn)));

    statement.setConditions(Collections.singletonList(
      new Condition().withType("ArnLike").withConditionKey("aws:SourceArn").withValues(sourceARN)
    ));

    return new Policy("allow-remote-account-send", Collections.singletonList(statement));
  }
}
