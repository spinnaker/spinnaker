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

package com.netflix.spinnaker.kork.plugins.update.release.source

import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import org.springframework.core.Ordered

/**
 * Source the desired release(s) from the provided [SpinnakerPluginInfo] list.
 *
 * A similar signature as PluginInfoReleaseProvider, but used to model releases from one specific
 * source.
 */
@Beta
interface PluginInfoReleaseSource : Ordered {

  /**
   * Get plugin releases from a list of plugin info objects
   */
  fun getReleases(pluginInfo: List<SpinnakerPluginInfo>): Set<PluginInfoRelease>

  /**
   * Optionally process releases (i.e., POST to front50/another service, modify in-memory set,
   * write to a filesystem, etc).
   */
  fun processReleases(pluginInfoReleases: Set<PluginInfoRelease>) { /* default implementation */ }
}
