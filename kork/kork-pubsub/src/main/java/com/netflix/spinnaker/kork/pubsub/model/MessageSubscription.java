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

package com.netflix.spinnaker.kork.pubsub.model;

import java.time.Duration;

/** A manually acknowledged subscription with an explicit lifecycle. Delivery is at least once. */
public interface MessageSubscription
    extends PubsubSubscriber,
        MessageAcknowledger<MessageSubscription, PubsubMessage>,
        AutoCloseable {
  void start();

  void stop();

  boolean isRunning();

  void ack(PubsubMessage message);

  void nack(PubsubMessage message);

  /** Re-deliver at most limit messages whose previous consumer has exceeded minIdle. */
  long reclaim(Duration minIdle, int limit);

  @Override
  default void ack(MessageSubscription subscription, PubsubMessage message) {
    subscription.ack(message);
  }

  @Override
  default void nack(MessageSubscription subscription, PubsubMessage message) {
    subscription.nack(message);
  }

  @Override
  void close();
}
