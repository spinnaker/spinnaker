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

package com.netflix.spinnaker.kork.pubsub.aws.api;

import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubProperties;

/**
 * To support SNS/SQS subscriptions, users of kork-pubsub-aws are expected to register a
 * AmazonPubsubMessageHandlerFactory bean so that an AmazonPubsubMessageHandlers can be associated
 * with a given subscription
 */
public interface AmazonPubsubMessageHandlerFactory {
  /**
   * @param subscription the configuration for a given SNS/SQS subscription
   * @return the AmazonPubsubMessageHandler instance that will handle messages coming from that
   *     queue
   */
  AmazonPubsubMessageHandler create(AmazonPubsubProperties.AmazonPubsubSubscription subscription);
}
