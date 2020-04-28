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

package com.netflix.spinnaker.kork.plugins.update.release.provider

import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import com.netflix.spinnaker.kork.plugins.update.release.source.PluginInfoReleaseSource
import org.pf4j.update.PluginInfo

/**
 * Provide the desired release(s) from the provided [PluginInfo] list.
 *
 * A similar signature as PluginInfoReleaseSource, but used by consumers to obtain a final set
 * of plugin releases.
 */
@Beta
interface PluginInfoReleaseProvider {

  /**
   * Get plugin releases from a list of plugin info objects
   */
  fun getReleases(pluginInfo: List<PluginInfo>): Set<PluginInfoRelease>
}

class PluginReleaseNotFoundException(pluginId: String, sources: List<PluginInfoReleaseSource>) :
  IntegrationException(
    "A release version of '$pluginId' was not sourced from the provider sources '$sources'"
  )
