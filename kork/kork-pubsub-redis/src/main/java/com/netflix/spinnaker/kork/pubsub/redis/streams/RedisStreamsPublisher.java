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

import com.netflix.spinnaker.kork.pubsub.model.PubsubPublisher;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

/** One publisher for each stream subscription. */
@Slf4j
public class RedisStreamsPublisher implements PubsubPublisher {
  /**
   * The field a {@link RedisStreamsPublisher} stores the message body under; readers pull it back
   * out of the raw {@code MapRecord} handed to their {@code RedisStreamMessageHandler}. Publish
   * attributes must not use this key.
   */
  public static final String MESSAGE_FIELD = "message";

  private final RedisPubsubProperties.RedisStreamSubscription subscription;
  private final StringRedisTemplate redisTemplate;
  private final MeterRegistry meterRegistry;

  public RedisStreamsPublisher(
      RedisPubsubProperties.RedisStreamSubscription subscription,
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry) {
    this.subscription = subscription;
    this.redisTemplate = redisTemplate;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public String getPubsubSystem() {
    return RedisPubsubConfig.SYSTEM;
  }

  @Override
  public String getTopicName() {
    return subscription.getStreamKey();
  }

  @Override
  public String getName() {
    return subscription.getName();
  }

  @Override
  public void publish(String message, Map<String, String> attributes) {
    Map<String, String> fields = new HashMap<>(attributes);
    fields.put(MESSAGE_FIELD, message);
    try {
      redisTemplate
          .opsForStream()
          .add(
              StreamRecords.newRecord().ofMap(fields).withStreamKey(subscription.getStreamKey()),
              XAddOptions.maxlen(subscription.getStreamMaxLength()).approximateTrimming(true));
      meterRegistry.counter("pubsub.redis.published", subscriptionTag()).increment();
    } catch (Exception e) {
      log.error("Failed to publish message to stream {}", subscription.getStreamKey(), e);
      meterRegistry
          .counter(
              "pubsub.redis.publishFailed",
              List.of(
                  Tag.of("subscription", subscription.getName()),
                  Tag.of("exceptionClass", e.getClass().getSimpleName())))
          .increment();
    }
  }

  private List<Tag> subscriptionTag() {
    return List.of(Tag.of("subscription", subscription.getName()));
  }
}
