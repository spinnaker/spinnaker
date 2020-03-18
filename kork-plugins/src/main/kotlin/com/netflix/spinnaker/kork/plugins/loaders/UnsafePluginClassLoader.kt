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
import java.net.URL
import java.net.URLClassLoader
import java.util.Enumeration
import org.pf4j.PluginClassLoader
import org.pf4j.PluginManager

/**
 * The [UnsafePluginClassLoader] allows a plugin to use the parent classloader. Caution should
 * be used while developing unsafe plugins, as they are given carte blanche integration into
 * the parent application.
 *
 * PF4J is a little wonky in that all class loaders must be a [PluginClassLoader]... so this extends
 * that class, and then just delegates everything to the provided [parent] [ClassLoader]. It's a little
 * wasteful, but the only way to do things and stay in the PF4J ecosystem at the moment.
 */
class UnsafePluginClassLoader(
  pluginManager: PluginManager,
  pluginDescriptor: SpinnakerPluginDescriptor,
  parent: ClassLoader
) : PluginClassLoader(pluginManager, pluginDescriptor, parent) {

  override fun loadClass(name: String?): Class<*> = parent.loadClass(name)
  override fun getResource(name: String?): URL? = parent.getResource(name)
  override fun getResources(name: String?): Enumeration<URL> = parent.getResources(name)

  override fun addURL(url: URL?) {
    val method = URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java)
    method.isAccessible = true
    method.invoke(parent, url)
  }
}
