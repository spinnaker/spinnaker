/*
 * Copyright 2020 Netflix, Inc.
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
 * An abstraction over specific pubsub systems, each subscription will have one publisher associated
 * with it It is possible that some subscriptions only allow reading from the subscriber, for
 * instance if the topic is owned by a different entity. In this case, calls to {@link
 * #publish(String, Map)} would be expected to fail.
 */
public interface PubsubPublisher {
  String getPubsubSystem();

  String getTopicName();

  String getName();

  /**
   * The system-agnostic way to publish messages to a topic. Concrete implementations may offer more
   * detailed publish methods that expose features of their particular pubsub system.
   *
   * @param message the body of the message to send
   * @param attributes key/value attribute pairs, if that pubsub system supports it
   */
  void publish(String message, Map<String, String> attributes);

  default void publish(String message) {
    publish(message, Collections.emptyMap());
  }
}
