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
package com.netflix.spinnaker.kork.plugins.loaders

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import java.nio.file.Path
import org.pf4j.JarPluginLoader
import org.pf4j.PluginDescriptor
import org.pf4j.PluginManager
import org.slf4j.LoggerFactory

/**
 * Allows altering the a plugin's [ClassLoader] based on the plugin's `unsafe` flag.
 */
class SpinnakerJarPluginLoader(
  pluginManager: PluginManager
) : JarPluginLoader(pluginManager) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun loadPlugin(pluginPath: Path, pluginDescriptor: PluginDescriptor): ClassLoader {
    if (pluginDescriptor !is SpinnakerPluginDescriptor) {
      log.debug(
        "Descriptor for ${pluginDescriptor.pluginId} is not SpinnakerPluginDescriptor: " +
          "Falling back to default behavior"
      )
      return super.loadPlugin(pluginPath, pluginDescriptor)
    }

    return if (pluginDescriptor.unsafe) {
      UnsafePluginClassLoader(
        pluginManager,
        pluginDescriptor,
        javaClass.classLoader
      )
    } else {
      super.loadPlugin(pluginPath, pluginDescriptor)
    }
  }
}
