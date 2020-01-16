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

import org.pf4j.CompoundPluginDescriptorFinder
import org.pf4j.PluginDescriptor
import org.pf4j.PluginDescriptorFinder
import org.pf4j.RuntimeMode
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Decorates [PluginDescriptor]s as [SpinnakerPluginDescriptor]s.
 *
 * @see SpinnakerManifestPluginDescriptorFinder
 * @see SpinnakerPropertiesPluginDescriptorFinder
 */
class SpinnakerPluginDescriptorFinder(
  private val runtimeMode: RuntimeMode,
  private val finders: CompoundPluginDescriptorFinder = CompoundPluginDescriptorFinder()
) : PluginDescriptorFinder {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  init {
    if (finders.size() > 0) {
      // It's not currently obvious if someone would need to provide their own finders, but
      // I want to support this anyway. Doing so could potentially break plugins in Spinnaker
      // if it's not done correctly, however.
      log.warn("Custom finders have been provided, skipping defaults")
    } else {
      fun addSpinnakerFinders(finder: CompoundPluginDescriptorFinder): CompoundPluginDescriptorFinder =
        finder
          .add(SpinnakerPropertiesPluginDescriptorFinder())
          .add(SpinnakerManifestPluginDescriptorFinder())

      if (runtimeMode == RuntimeMode.DEVELOPMENT) {
        val spinnakerFinders = addSpinnakerFinders(CompoundPluginDescriptorFinder())
        finders
          .add(PluginRefPluginDescriptorFinder(spinnakerFinders))
          .add(spinnakerFinders)
      } else {
        addSpinnakerFinders(finders)
      }
    }
  }

  override fun isApplicable(pluginPath: Path): Boolean =
    finders.isApplicable(pluginPath)

  override fun find(pluginPath: Path): PluginDescriptor =
    finders.find(pluginPath)
}
