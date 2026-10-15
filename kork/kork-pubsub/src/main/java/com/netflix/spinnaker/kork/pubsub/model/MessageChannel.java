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

import java.util.function.Consumer;

/** A publisher that can also create independently managed subscriptions. */
public interface MessageChannel extends PubsubPublisher {
  MessageSubscription subscribe(
      String name,
      int batchSize,
      Consumer<PubsubMessage> handler,
      Consumer<Throwable> errorHandler);

  default void trim(long maxLength) {}

  default Statistics statistics() {
    return new Statistics(0, 0, 0);
  }

  record Statistics(long depth, long pending, long consumers) {}
}
