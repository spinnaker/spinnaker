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

package com.netflix.spinnaker.kork.pubsub.redis.streams.api;

import org.springframework.data.redis.connection.stream.MapRecord;

/**
 * Each stream subscription's worker pool is associated with a single RedisStreamMessageHandler.
 *
 * @see RedisStreamMessageHandlerFactory
 */
public interface RedisStreamMessageHandler {
  /**
   * The callback invoked when a record is read from the stream. Implementations can throw
   * exceptions out of handleMessage; they will be handled by the caller.
   *
   * @param record the raw stream record read from Redis
   */
  void handleMessage(MapRecord<String, String, String> record);
}
