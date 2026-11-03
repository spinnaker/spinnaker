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
 * One broadcast subscriber exists for each broadcast subscription, and facilitates getting every
 * message published to that subscription's channel - as opposed to {@link PubsubSubscriber}, where
 * only one subscriber across a fleet of competing consumers receives any given message.
 *
 * <p>There is no acknowledgement concept here: a broadcast message that a handler fails to process
 * is logged/counted by the implementation, never redelivered, unless the implementation's {@link
 * #getDeliveryGuarantee()} is {@link DeliveryGuarantee#AT_LEAST_ONCE}, in which case redelivery
 * policy is that implementation's own concern.
 */
public interface PubsubBroadcastSubscriber {
  String getPubsubSystem();

  String getSubscriptionName();

  String getName();

  /**
   * The delivery guarantee this implementation offers. Defaults to {@link
   * DeliveryGuarantee#AT_MOST_ONCE}; see {@link PubsubBroadcastPublisher#getDeliveryGuarantee()}.
   */
  default DeliveryGuarantee getDeliveryGuarantee() {
    return DeliveryGuarantee.AT_MOST_ONCE;
  }
}
