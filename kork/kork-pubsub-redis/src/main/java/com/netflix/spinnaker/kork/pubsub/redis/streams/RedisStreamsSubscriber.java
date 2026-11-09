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

import com.netflix.spinnaker.kork.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;

/**
 * A lightweight registration object for a stream subscription - unlike AWS's {@code SQSSubscriber},
 * this does not own a thread: {@link RedisStreamsSubscriberProvider} multiplexes every configured
 * subscription onto one shared {@code StreamMessageListenerContainer}.
 */
public class RedisStreamsSubscriber implements PubsubSubscriber {
  private final RedisPubsubProperties.RedisStreamSubscription subscription;

  public RedisStreamsSubscriber(RedisPubsubProperties.RedisStreamSubscription subscription) {
    this.subscription = subscription;
  }

  @Override
  public String getPubsubSystem() {
    return RedisPubsubConfig.SYSTEM;
  }

  @Override
  public String getSubscriptionName() {
    return subscription.getName();
  }

  @Override
  public String getName() {
    return getSubscriptionName();
  }
}
