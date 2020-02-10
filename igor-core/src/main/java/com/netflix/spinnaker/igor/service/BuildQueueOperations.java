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
package com.netflix.spinnaker.igor.service;

/** Additional operations for {@link BuildService}s that support build queuing. */
public interface BuildQueueOperations<T> extends BuildOperations {
  /** Get the queued build at {@code queueId}. */
  T getQueuedBuild(String queueId);

  /**
   * Stop a queued build at {@code queueId}.
   *
   * <p>Will not wait for a result. Must be idempotent.
   */
  default void stopQueuedBuild(String jobName, String queueId, int buildNumber) {
    // Do nothing.
  }
}
