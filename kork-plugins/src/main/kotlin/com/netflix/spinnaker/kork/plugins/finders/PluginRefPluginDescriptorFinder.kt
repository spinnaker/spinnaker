/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.kork.plugins.finders

import com.netflix.spinnaker.kork.plugins.pluginref.PluginRef
import org.pf4j.PluginDescriptor
import org.pf4j.PluginDescriptorFinder
import java.nio.file.Path

/**
 * A [PluginDescriptorFinder] that uses a [PluginRef] to determine the root directory to
 * then delegate to the provided [descriptorFinder].
 */
class PluginRefPluginDescriptorFinder(private val descriptorFinder: PluginDescriptorFinder) : PluginDescriptorFinder {
  override fun isApplicable(pluginPath: Path?): Boolean {
    if (!PluginRef.isPluginRef(pluginPath)) {
      return false
    }

    val ref = PluginRef.loadPluginRef(pluginPath)
    return descriptorFinder.isApplicable(ref.refPath)
  }

  override fun find(pluginPath: Path?): PluginDescriptor {
    val ref = PluginRef.loadPluginRef(pluginPath)
    return descriptorFinder.find(ref.refPath)
  }
}
