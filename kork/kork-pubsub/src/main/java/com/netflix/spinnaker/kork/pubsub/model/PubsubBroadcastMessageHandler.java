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

/**
 * Business logic invoked for every {@link BroadcastMessage} a {@link PubsubBroadcastSubscriber}
 * receives. Unlike a competing-consumer handler, there is no ack/nack contract: an exception thrown
 * here should be logged and counted by the calling implementation, not retried - for an {@link
 * DeliveryGuarantee#AT_MOST_ONCE} implementation there is nothing to redeliver, and for an {@link
 * DeliveryGuarantee#AT_LEAST_ONCE} implementation redelivery policy is that implementation's own
 * concern, not this handler's.
 *
 * <p>Handlers should return quickly: an implementation may deliver a subscription's messages one at
 * a time, so a slow handler delays the messages behind it. An {@link
 * DeliveryGuarantee#AT_MOST_ONCE} implementation may drop messages (and should count them) rather
 * than buffer without bound once a handler falls too far behind.
 */
public interface PubsubBroadcastMessageHandler {
  void handleMessage(BroadcastMessage message);
}
