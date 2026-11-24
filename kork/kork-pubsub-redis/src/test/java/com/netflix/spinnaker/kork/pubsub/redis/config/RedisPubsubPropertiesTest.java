/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.kork.pubsub.redis.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RedisPubsubPropertiesTest {
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void defaultsAreValid() {
    RedisPubsubProperties properties = new RedisPubsubProperties();
    properties.setSubscriptions(List.of(subscription("a")));
    properties.setBroadcasts(List.of(broadcast("b")));

    assertThat(validator.validate(properties)).isEmpty();
  }

  @Test
  void nonPositiveLimitsAreRejected() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription("a");
    subscription.setMaxNumberOfMessages(0);
    subscription.setReclaimIntervalSeconds(0);
    subscription.setStreamMaxLength(0);
    subscription.setMaxDeliveryAttempts(0);
    subscription.setMinIdleTimeForClaim(null);
    RedisPubsubProperties properties = new RedisPubsubProperties();
    properties.setSubscriptions(List.of(subscription));

    assertThat(violatedProperties(validator.validate(properties)))
        .containsExactlyInAnyOrder(
            "subscriptions[0].maxNumberOfMessages",
            "subscriptions[0].reclaimIntervalSeconds",
            "subscriptions[0].streamMaxLength",
            "subscriptions[0].maxDeliveryAttempts",
            "subscriptions[0].minIdleTimeForClaim");
  }

  @Test
  void duplicateNamesAreRejected() {
    RedisPubsubProperties properties = new RedisPubsubProperties();
    properties.setSubscriptions(List.of(subscription("a"), subscription("a")));
    properties.setBroadcasts(List.of(broadcast("b"), broadcast("b")));

    assertThat(violatedProperties(validator.validate(properties)))
        .containsExactlyInAnyOrder("subscriptionNamesUnique", "broadcastNamesUnique");
  }

  @Test
  void invalidConfigurationFailsContextStartup() {
    new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig.class)
        .withPropertyValues(
            "pubsub.redis.subscriptions[0].name=a",
            "pubsub.redis.subscriptions[0].maxNumberOfMessages=0")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void deadLetterStreamDefaultsFromStreamKey() {
    RedisPubsubProperties.RedisStreamSubscription subscription = subscription("a");
    subscription.setStreamKey("my-stream");

    assertThat(subscription.getDeadLetterStreamKey()).isEqualTo("my-stream.dead-letter");
    assertThat(subscription.getMinIdleTimeForClaim()).isEqualTo(Duration.ofMinutes(5));
  }

  private static List<String> violatedProperties(
      Set<ConstraintViolation<RedisPubsubProperties>> violations) {
    return violations.stream().map(v -> v.getPropertyPath().toString()).toList();
  }

  private static RedisPubsubProperties.RedisStreamSubscription subscription(String name) {
    RedisPubsubProperties.RedisStreamSubscription subscription =
        new RedisPubsubProperties.RedisStreamSubscription();
    subscription.setName(name);
    return subscription;
  }

  private static RedisPubsubProperties.RedisBroadcastSubscription broadcast(String name) {
    RedisPubsubProperties.RedisBroadcastSubscription broadcast =
        new RedisPubsubProperties.RedisBroadcastSubscription();
    broadcast.setName(name);
    return broadcast;
  }

  @Configuration
  @EnableConfigurationProperties(RedisPubsubProperties.class)
  static class PropertiesConfig {}
}
