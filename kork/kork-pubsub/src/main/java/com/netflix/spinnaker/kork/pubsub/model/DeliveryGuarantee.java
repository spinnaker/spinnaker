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
 * Describes the delivery guarantee a broadcast implementation offers. This is a property of the
 * backing transport (and, for some transports, its configuration), not of the broadcast abstraction
 * itself: a native pub/sub transport with no persistence can only offer {@link #AT_MOST_ONCE},
 * while a transport that persists messages until every listener has consumed them (e.g. a
 * per-listener consumer group on a durable log) can offer {@link #AT_LEAST_ONCE}.
 */
public enum DeliveryGuarantee {
  /** A listener that is not actively connected when a message is published will never see it. */
  AT_MOST_ONCE,

  /**
   * A listener will eventually see every message published after it started listening, even if it
   * is briefly disconnected, at the cost of possible duplicate delivery.
   */
  AT_LEAST_ONCE
}
