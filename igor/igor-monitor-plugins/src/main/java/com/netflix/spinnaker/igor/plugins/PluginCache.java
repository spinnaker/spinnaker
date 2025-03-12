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
 *
 */
package com.netflix.spinnaker.igor.plugins;

import java.time.Instant;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Responsible for tracking seen plugin releases. */
public interface PluginCache {

  /**
   * Sets the last time the poller updated for the provided pluginId.
   *
   * <p>This should only be called when a new plugin release has been discovered, using the release
   * date of the plugin release as the timestamp.
   */
  void setLastPollCycle(@Nonnull String pluginId, @Nonnull Instant timestamp);

  /**
   * Get the last time the poller indexed the given pluginId. It will return null if no cache record
   * exists.
   *
   * @param pluginId
   * @return
   */
  @Nullable
  Instant getLastPollCycle(@Nonnull String pluginId);

  /** List all latest poll cycle timestamps, indexed by plugin ID. */
  @Nonnull
  Map<String, Instant> listLastPollCycles();
}
