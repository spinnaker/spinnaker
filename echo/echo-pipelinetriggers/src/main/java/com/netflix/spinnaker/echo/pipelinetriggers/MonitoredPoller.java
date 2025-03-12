/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers;

import com.netflix.spinnaker.echo.pipelinetriggers.health.MonitoredPollerHealth;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * A class that does some kind of polling operation and can be monitored by {@link
 * MonitoredPollerHealth}.
 */
public interface MonitoredPoller {
  /** @return `true` if polling is currently running, `false` otherwise. */
  boolean isRunning();

  /**
   * @return `true` if the initial polling cycle has completed successfully and there is some data
   *     available
   */
  default boolean isInitialized() {
    return getLastPollTimestamp() != null;
  }

  /** @return the time at which the last successful poll operation was run. */
  @Nullable
  Instant getLastPollTimestamp();

  /** @return the interval between poll operations in seconds. */
  int getPollingIntervalSeconds();
}
