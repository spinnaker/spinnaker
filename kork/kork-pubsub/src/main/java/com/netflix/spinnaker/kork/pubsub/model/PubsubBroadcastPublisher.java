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

package com.netflix.spinnaker.kork.pubsub.model;

import java.util.Collections;
import java.util.Map;

/**
 * An abstraction over specific pubsub systems for broadcast delivery: a published message is
 * expected to be picked up by every currently-listening {@link PubsubBroadcastSubscriber}, not just
 * one competing consumer. Contrast with {@link PubsubPublisher}, which a competing-consumer
 * ("single delivery") implementation uses instead.
 */
public interface PubsubBroadcastPublisher {
  String getPubsubSystem();

  String getTopicName();

  String getName();

  /**
   * The system-agnostic way to broadcast messages to a channel. Concrete implementations may offer
   * more detailed publish methods that expose features of their particular pubsub system.
   *
   * @param message the body of the message to send
   * @param attributes key/value attribute pairs, if that pubsub system supports it
   */
  void publish(String message, Map<String, String> attributes);

  default void publish(String message) {
    publish(message, Collections.emptyMap());
  }

  /**
   * The delivery guarantee this implementation offers. Defaults to {@link
   * DeliveryGuarantee#AT_MOST_ONCE}, the weakest guarantee any broadcast transport can offer;
   * implementations backed by a durable transport (or configured to use one) should override this.
   */
  default DeliveryGuarantee getDeliveryGuarantee() {
    return DeliveryGuarantee.AT_MOST_ONCE;
  }
}
