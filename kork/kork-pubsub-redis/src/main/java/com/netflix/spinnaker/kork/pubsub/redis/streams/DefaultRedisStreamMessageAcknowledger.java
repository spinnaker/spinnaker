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

package com.netflix.spinnaker.kork.pubsub.redis.streams;

import com.netflix.spinnaker.kork.pubsub.redis.streams.api.RedisStreamMessageAcknowledger;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
public class DefaultRedisStreamMessageAcknowledger implements RedisStreamMessageAcknowledger {
  private final StringRedisTemplate redisTemplate;
  private final MeterRegistry meterRegistry;

  public DefaultRedisStreamMessageAcknowledger(
      StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
    this.redisTemplate = redisTemplate;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void ack(
      RedisStreamSubscriptionInformation subscription, MapRecord<String, String, String> record) {
    try {
      redisTemplate
          .opsForStream()
          .acknowledge(
              subscription.getStreamKey(), subscription.getConsumerGroup(), record.getId());
      meterRegistry.counter("pubsub.redis.acked", subscriptionTag(subscription)).increment();
    } catch (Exception e) {
      log.warn(
          "Error acknowledging record {} for subscription {}",
          record.getId(),
          subscription.getSubscription().getName(),
          e);
      meterRegistry
          .counter(
              "pubsub.redis.ackFailed",
              List.of(
                  Tag.of("subscription", subscription.getSubscription().getName()),
                  Tag.of("exceptionClass", e.getClass().getSimpleName())))
          .increment();
    }
  }

  @Override
  public void nack(
      RedisStreamSubscriptionInformation subscription, MapRecord<String, String, String> record) {
    // Do nothing - the record stays pending, and the periodic reclaim loop's XCLAIM is the only
    // redelivery path (the Redis analog of SQS leaving a message for its visibility timeout to
    // expire).
    meterRegistry.counter("pubsub.redis.nacked", subscriptionTag(subscription)).increment();
  }

  private List<Tag> subscriptionTag(RedisStreamSubscriptionInformation subscription) {
    return List.of(Tag.of("subscription", subscription.getSubscription().getName()));
  }
}
