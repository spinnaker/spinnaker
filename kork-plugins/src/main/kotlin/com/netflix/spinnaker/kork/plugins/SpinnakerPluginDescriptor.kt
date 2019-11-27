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
package com.netflix.spinnaker.kork.plugins

import org.pf4j.PluginDescriptor

/**
 * Decorates the default [PluginDescriptor] with additional Spinnaker-specific metadata.
 *
 * @param namespace The plugin namespace. This should be the name of your org (github username, org, etc).
 * @param unsafe If set to true, a plugin will be created using the parent application ClassLoader.
 */
class SpinnakerPluginDescriptor(
  private val baseDescriptor: PluginDescriptor,
  val namespace: String,
  val unsafe: Boolean = false,
  val pluginName: String = baseDescriptor.pluginId
) : PluginDescriptor by baseDescriptor {
  override fun getPluginId(): String {
    if (namespace == "undefined") {
      return baseDescriptor.pluginId
    } else {
      return "$namespace.${baseDescriptor.pluginId}"
    }
  }
}
