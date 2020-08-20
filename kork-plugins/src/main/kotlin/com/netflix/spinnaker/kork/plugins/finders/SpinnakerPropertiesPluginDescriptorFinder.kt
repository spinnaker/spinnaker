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

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.validate
import java.util.Properties
import org.pf4j.DefaultPluginDescriptor
import org.pf4j.PluginDescriptor
import org.pf4j.PropertiesPluginDescriptorFinder

/**
 * Decorates the default [PluginDescriptor] created from [PropertiesPluginDescriptorFinder]
 * with Spinnaker-specific descriptor metadata.
 */
class SpinnakerPropertiesPluginDescriptorFinder(
  propertiesFileName: String = DEFAULT_PROPERTIES_FILE_NAME
) : PropertiesPluginDescriptorFinder(propertiesFileName) {

  override fun createPluginDescriptor(properties: Properties): PluginDescriptor =
    super.createPluginDescriptor(properties).also {
      if (it is SpinnakerPluginDescriptor) {
        it.unsafe = properties.getProperty(PLUGIN_UNSAFE)?.toBoolean() ?: false
      }
      it.validate()
    }

  override fun createPluginDescriptorInstance(): DefaultPluginDescriptor = SpinnakerPluginDescriptor()

  companion object {
    const val PLUGIN_UNSAFE = "plugin.unsafe"
  }
}
