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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.aws.ARN;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubProperties;
import com.netflix.spinnaker.kork.pubsub.model.PubsubPublisher;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/** One publisher for each topic */
public class SNSPublisher implements PubsubPublisher {
  private static final Logger log = LoggerFactory.getLogger(SNSPublisher.class);

  private final SnsClient snsClient;
  private final AmazonPubsubProperties.AmazonPubsubSubscription subscription;
  private final Registry registry;
  private final Supplier<Boolean> isEnabled;

  private final ARN topicARN;
  private final RetrySupport retrySupport;
  private Counter successCounter;

  public SNSPublisher(
      AmazonPubsubProperties.AmazonPubsubSubscription subscription,
      SnsClient snsClient,
      Supplier<Boolean> isEnabled,
      Registry registry,
      RetrySupport retrySupport) {
    this.subscription = subscription;
    this.snsClient = snsClient;
    this.isEnabled = isEnabled;
    this.registry = registry;
    this.topicARN = new ARN(subscription.getTopicARN());
    this.successCounter = registry.counter("pubsub.amazon.published", "topic", getTopicName());
    this.retrySupport = retrySupport;

    initializeTopic();
  }

  @Override
  public String getPubsubSystem() {
    return AmazonPubsubConfig.SYSTEM;
  }

  @Override
  public String getTopicName() {
    return subscription.getName();
  }

  @Override
  public String getName() {
    return getTopicName();
  }

  private void initializeTopic() {
    PubSubUtils.ensureTopicExists(snsClient, topicARN, subscription);
  }

  @Override
  public void publish(String message, Map<String, String> attributes) {
    publishMessage(message);
  }

  public Optional<PublishResponse> publishMessage(String message) {
    if (!isEnabled.get()) {
      log.warn("Publishing is disabled for topic {}, dropping message {}", topicARN, message);
      return Optional.empty();
    }

    try {
      PublishRequest publishRequest =
          PublishRequest.builder().topicArn(topicARN.getArn()).message(message).build();
      PublishResponse publishResponse =
          retrySupport.retry(
              () -> snsClient.publish(publishRequest), 5, Duration.ofMillis(200), false);

      log.debug(
          "Published message {} with id {} to topic {}",
          message,
          publishResponse.messageId(),
          topicARN);
      getSuccessCounter().increment();
      return Optional.of(publishResponse);
    } catch (Exception e) {
      log.error("failed to publish message {} to topic {}", message, topicARN, e);
      getErrorCounter(e).increment();
      return Optional.empty();
    }
  }

  private Counter getSuccessCounter() {
    return successCounter;
  }

  private Counter getErrorCounter(Exception e) {
    return registry.counter(
        "pubsub.amazon.publishFailed",
        "topic",
        getTopicName(),
        "exceptionClass",
        e.getClass().getSimpleName());
  }
}
