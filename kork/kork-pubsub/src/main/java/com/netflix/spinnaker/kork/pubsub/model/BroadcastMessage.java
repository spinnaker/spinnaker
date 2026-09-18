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
import lombok.Builder;
import lombok.Value;

/**
 * A transport-agnostic envelope for a broadcast message. Unlike a {@link PubsubSubscriber}'s
 * incoming message (which is transport-specific, e.g. an SQS {@code Message} or a Redis Streams
 * {@code MapRecord}, and carries redelivery metadata such as a receipt handle), a broadcast message
 * has nothing to redeliver, so a single envelope type is enough for every transport.
 */
@Value
@Builder
public class BroadcastMessage {
  String channel;
  String body;
  @Builder.Default Map<String, String> attributes = Collections.emptyMap();
}
