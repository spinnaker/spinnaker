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

import com.netflix.spinnaker.kork.plugins.update.release.PluginInfoRelease
import org.pf4j.update.PluginInfo
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered.LOWEST_PRECEDENCE

class Front50PluginInfoReleaseSource : PluginInfoReleaseSource {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun getReleases(pluginInfo: List<PluginInfo>): Set<PluginInfoRelease> {
    return mutableSetOf()
  }

  override fun processReleases(pluginInfoReleases: Set<PluginInfoRelease>) {
    // Front50 stuff to sync / pin release
  }

  /**
   * Ensures this runs last in
   * [com.netflix.spinnaker.kork.plugins.update.release.provider.AggregatePluginInfoReleaseProvider]
   */
  override fun getOrder(): Int = LOWEST_PRECEDENCE
}
