/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.kork.pubsub.redis;

import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.kork.pubsub.model.MessageChannel;
import com.netflix.spinnaker.kork.pubsub.model.MessageSubscription;
import com.netflix.spinnaker.kork.pubsub.model.PubsubMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisMessageChannel implements MessageChannel {
  static final String PAYLOAD_FIELD = "_korkPayload";
  private final String topic;
  private final String streamKey;
  private final String group;
  private final StringRedisTemplate redis;
  private final PubsubSubscribers subscribers;

  RedisMessageChannel(
      String topic,
      String streamKey,
      String group,
      StringRedisTemplate redis,
      PubsubSubscribers subscribers) {
    this.topic = topic;
    this.streamKey = streamKey;
    this.group = group;
    this.redis = redis;
    this.subscribers = subscribers;
  }

  @Override
  public String getPubsubSystem() {
    return "redis";
  }

  @Override
  public String getTopicName() {
    return topic;
  }

  @Override
  public String getName() {
    return topic + (group == null ? ":broadcast" : ":" + group);
  }

  public String getStreamKey() {
    return streamKey;
  }

  @Override
  public void publish(String message, Map<String, String> attributes) {
    if (attributes.containsKey(PAYLOAD_FIELD)) {
      throw new IllegalArgumentException("Reserved attribute: " + PAYLOAD_FIELD);
    }
    Map<String, String> fields = new HashMap<>(attributes);
    fields.put(PAYLOAD_FIELD, message);
    redis.opsForStream().add(streamKey, fields);
  }

  @Override
  public MessageSubscription subscribe(
      String name,
      int batchSize,
      Consumer<PubsubMessage> handler,
      Consumer<Throwable> errorHandler) {
    RedisMessageSubscription subscription =
        new RedisMessageSubscription(
            redis, streamKey, group, name, batchSize, handler, errorHandler, subscribers);
    subscribers.putAll(List.of(subscription));
    return subscription;
  }

  @Override
  public void trim(long maxLength) {
    redis.opsForStream().trim(streamKey, maxLength, true);
  }

  @Override
  public Statistics statistics() {
    Long depth = redis.opsForStream().size(streamKey);
    if (group == null) {
      return new Statistics(depth == null ? 0 : depth, 0, 0);
    }
    StreamInfo.XInfoGroups groups = redis.opsForStream().groups(streamKey);
    return groups.stream()
        .filter(info -> group.equals(info.groupName()))
        .map(
            info ->
                new Statistics(
                    depth == null ? 0 : depth, info.pendingCount(), info.consumerCount()))
        .findFirst()
        .orElse(new Statistics(depth == null ? 0 : depth, 0, 0));
  }
}
