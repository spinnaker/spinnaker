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

import com.netflix.spinnaker.kork.pubsub.PubsubPublishers;
import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis Streams channels registered with Kork's existing pubsub infrastructure. */
public class RedisPubsub {
  private final StringRedisTemplate redis;
  private final PubsubPublishers publishers;
  private final PubsubSubscribers subscribers;

  public RedisPubsub(
      StringRedisTemplate redis, PubsubPublishers publishers, PubsubSubscribers subscribers) {
    this.redis = redis;
    this.publishers = publishers;
    this.subscribers = subscribers;
  }

  /** Only subscribers of the selected logical group can read this group's stream. */
  public RedisMessageChannel targeted(String topic, String group) {
    String suffix =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(group.getBytes(StandardCharsets.UTF_8));
    return bindTargetedStream(topic, group, topic + ":target:" + suffix);
  }

  /** Bind a pre-existing stream dedicated exclusively to this group, preserving its wire format. */
  public RedisMessageChannel bindTargetedStream(String topic, String group, String streamKey) {
    return register(new RedisMessageChannel(topic, streamKey, group, redis, subscribers));
  }

  /** Each subscription has its own group and starts at the current end of the stream. */
  public RedisMessageChannel broadcast(String topic) {
    return register(new RedisMessageChannel(topic, topic + ":broadcast", null, redis, subscribers));
  }

  private RedisMessageChannel register(RedisMessageChannel channel) {
    publishers.putAll(List.of(channel));
    return channel;
  }
}
