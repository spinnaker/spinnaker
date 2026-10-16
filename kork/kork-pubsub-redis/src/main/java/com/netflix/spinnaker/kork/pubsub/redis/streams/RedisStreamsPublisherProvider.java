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

import com.netflix.spinnaker.kork.pubsub.PubsubPublishers;
import com.netflix.spinnaker.kork.pubsub.model.PubsubPublisher;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Creates one {@link RedisStreamsPublisher} per configured stream subscription. */
@Component
@ConditionalOnProperty({"pubsub.enabled", "pubsub.redis.enabled"})
public class RedisStreamsPublisherProvider {
  private static final Logger log = LoggerFactory.getLogger(RedisStreamsPublisherProvider.class);

  private final RedisPubsubProperties properties;
  private final PubsubPublishers pubsubPublishers;
  private final StringRedisTemplate redisTemplate;
  private final MeterRegistry meterRegistry;

  @Autowired
  public RedisStreamsPublisherProvider(
      RedisPubsubProperties properties,
      PubsubPublishers pubsubPublishers,
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.pubsubPublishers = pubsubPublishers;
    this.redisTemplate = redisTemplate;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  public void start() {
    List<PubsubPublisher> publishers = new ArrayList<>();
    properties
        .getSubscriptions()
        .forEach(
            subscription -> {
              log.info("Bootstrapping Redis Streams publisher for {}", subscription.getStreamKey());
              publishers.add(new RedisStreamsPublisher(subscription, redisTemplate, meterRegistry));
            });
    pubsubPublishers.putAll(publishers);
  }
}
