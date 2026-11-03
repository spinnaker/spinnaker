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

package com.netflix.spinnaker.kork.pubsub.redis.broadcast.api;

import com.netflix.spinnaker.kork.pubsub.model.PubsubBroadcastMessageHandler;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;

/**
 * To support Redis broadcast subscriptions, users of kork-pubsub-redis are expected to register a
 * RedisBroadcastMessageHandlerFactory bean so that a handler can be associated with a given
 * subscription. Unlike the Streams side, this returns the base {@link
 * PubsubBroadcastMessageHandler} directly - a broadcast message has nothing transport-specific
 * about it, so no Redis-specific handler subtype is needed.
 */
public interface RedisBroadcastMessageHandlerFactory {
  /**
   * @param subscription the configuration for a given broadcast subscription
   * @return the handler instance that will handle messages coming from that subscription's channel
   */
  PubsubBroadcastMessageHandler create(
      RedisPubsubProperties.RedisBroadcastSubscription subscription);
}
