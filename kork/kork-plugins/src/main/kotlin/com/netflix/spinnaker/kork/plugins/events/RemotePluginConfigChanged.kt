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

package com.netflix.spinnaker.kork.plugins.events

import com.netflix.spinnaker.kork.api.plugins.remote.RemoteExtensionConfig
import com.netflix.spinnaker.kork.plugins.update.release.remote.RemotePluginInfoReleaseCache
import org.springframework.context.ApplicationEvent

/**
 * A Spring [ApplicationEvent] that is emitted when a remote plugin configuration is changed.
 *
 * @param source The source of the event
 * @param status Whether the remote plugin config is [ENABLED], [DISABLED], or [UPDATED].  [ENABLED]
 * and [DISABLED] are self-explanatory. [UPDATED] occurs when a plugin is already enabled and the
 * version changes.
 * @param pluginId The plugin ID for the remote extension
 * @param version The plugin release version
 * @param remoteExtensionConfigs A list of remote extension configs associated with the plugin release
 */
data class RemotePluginConfigChanged(
  private val source: RemotePluginInfoReleaseCache,
  val status: Status,
  val pluginId: String,
  val version: String,
  val remoteExtensionConfigs: List<RemoteExtensionConfig>
) : ApplicationEvent(source) {
  /**
   * The type of config change.
   */
  enum class Status {
    ENABLED,
    DISABLED,
    UPDATED
  }
}
