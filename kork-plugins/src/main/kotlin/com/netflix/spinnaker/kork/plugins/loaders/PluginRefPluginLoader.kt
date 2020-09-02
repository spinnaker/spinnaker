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
import com.netflix.spinnaker.kork.plugins.pluginref.PluginRef
import java.nio.file.Path
import org.pf4j.BasePluginLoader
import org.pf4j.PluginClassLoader
import org.pf4j.PluginClasspath
import org.pf4j.PluginDescriptor
import org.pf4j.PluginLoader
import org.pf4j.PluginManager

/**
 * A [PluginLoader] that can produce a [PluginClassLoader] from a [PluginRef].
 */
class PluginRefPluginLoader(private val pluginManager: PluginManager) : PluginLoader {
  override fun loadPlugin(pluginPath: Path?, pluginDescriptor: PluginDescriptor?): ClassLoader {
    if (pluginDescriptor is SpinnakerPluginDescriptor && pluginDescriptor.unsafe) {
      return UnsafePluginClassLoader(pluginManager, pluginDescriptor, javaClass.classLoader)
    }

    val ref = PluginRef.loadPluginRef(pluginPath)

    val cp = PluginClasspath()
    cp.addClassesDirectories(ref.classesDirs)
    cp.addJarsDirectories(ref.libsDirs)

    return BasePluginLoader(pluginManager, cp).loadPlugin(ref.refPath, pluginDescriptor)
  }

  override fun isApplicable(pluginPath: Path?): Boolean = PluginRef.isPluginRef(pluginPath)
}
