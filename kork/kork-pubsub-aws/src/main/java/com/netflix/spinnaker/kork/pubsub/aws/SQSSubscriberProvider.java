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

import com.google.common.base.Preconditions;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.aws.ARN;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonMessageAcknowledger;
import com.netflix.spinnaker.kork.pubsub.aws.api.AmazonPubsubMessageHandlerFactory;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubProperties;
import com.netflix.spinnaker.kork.pubsub.model.PubsubSubscriber;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/** Starts the individual SQS workers (one for each subscription) */
@Component
@ConditionalOnProperty({"pubsub.enabled", "pubsub.amazon.enabled"})
public class SQSSubscriberProvider {
  private static final Logger log = LoggerFactory.getLogger(SQSSubscriberProvider.class);

  private final AwsCredentialsProvider awsCredentialsProvider;
  private final AmazonPubsubProperties properties;
  private final PubsubSubscribers pubsubSubscribers;
  private final AmazonPubsubMessageHandlerFactory pubsubMessageHandlerFactory;
  private final Registry registry;
  private final DiscoveryStatusListener discoveryStatus;
  private final DynamicConfigService dynamicConfig;
  private final AmazonMessageAcknowledger messageAcknowledger;

  @Autowired
  public SQSSubscriberProvider(
      AwsCredentialsProvider awsCredentialsProvider,
      AmazonPubsubProperties properties,
      PubsubSubscribers pubsubSubscribers,
      AmazonPubsubMessageHandlerFactory pubsubMessageHandlerFactory,
      AmazonMessageAcknowledger messageAcknowledger,
      Registry registry,
      DiscoveryStatusListener discoveryStatus,
      DynamicConfigService dynamicConfig) {
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.properties = properties;
    this.pubsubSubscribers = pubsubSubscribers;
    this.pubsubMessageHandlerFactory = pubsubMessageHandlerFactory;
    this.messageAcknowledger = messageAcknowledger;
    this.registry = registry;
    this.discoveryStatus = discoveryStatus;
    this.dynamicConfig = dynamicConfig;
  }

  @PostConstruct
  public void start() {
    Preconditions.checkNotNull(
        properties, "Can't initialize SQSSubscriberProvider with null properties");

    ExecutorService executorService =
        Executors.newFixedThreadPool(properties.getSubscriptions().size());

    List<PubsubSubscriber> subscribers = new ArrayList<>();

    properties
        .getSubscriptions()
        .forEach(
            (AmazonPubsubProperties.AmazonPubsubSubscription subscription) -> {
              log.info("Bootstrapping SQS for SNS topic: {}", subscription.getTopicARN());
              ARN queueArn = new ARN(subscription.getQueueARN());
              ARN topicArn = new ARN(subscription.getTopicARN());
              Region queueRegion = Region.of(queueArn.getRegion());
              Region topicRegion = Region.of(topicArn.getRegion());

              SqsClient sqsClient =
                  SqsClient.builder()
                      .credentialsProvider(awsCredentialsProvider)
                      .region(queueRegion)
                      .build();
              SnsClient snsClient =
                  SnsClient.builder()
                      .credentialsProvider(awsCredentialsProvider)
                      .region(topicRegion)
                      .build();

              SQSSubscriber worker =
                  new SQSSubscriber(
                      subscription,
                      pubsubMessageHandlerFactory.create(subscription),
                      messageAcknowledger,
                      snsClient,
                      sqsClient,
                      PubSubUtils.getEnabledSupplier(dynamicConfig, subscription, discoveryStatus),
                      registry);
              try {
                executorService.submit(worker);
                subscribers.add(worker);
                log.debug(
                    "Created worker {} for subscription: {}",
                    worker.getWorkerName(),
                    subscription.getName());
              } catch (RejectedExecutionException e) {
                log.error("Could not start {}", worker.getWorkerName(), e);
              }
            });

    pubsubSubscribers.putAll(subscribers);
  }
}
