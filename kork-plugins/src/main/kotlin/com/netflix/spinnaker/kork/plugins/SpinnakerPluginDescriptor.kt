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

import java.util.Objects
import org.pf4j.DefaultPluginDescriptor
import org.pf4j.PluginDependency
import org.pf4j.PluginDescriptor

/**
 * Decorates the default [PluginDescriptor] with additional Spinnaker-specific metadata.
 *
 * @param unsafe If set to true, a plugin will be created using the parent application ClassLoader.
 */
class SpinnakerPluginDescriptor(
  pluginId: String? = null,
  pluginDescription: String? = null,
  pluginClass: String? = null,
  version: String? = null,
  requires: String? = null,
  provider: String? = null,
  license: String? = null,
  var unsafe: Boolean = false
) : DefaultPluginDescriptor(
  pluginId,
  pluginDescription,
  pluginClass,
  version,
  requires ?: "",
  provider,
  license) {

  // Jackson compatible private setter
  private fun setDependencies(dependencies: List<PluginDependency>) {
    this.setDependencies(dependencies.joinToString(","))
  }

  // TODO(cf): rework once upstream equals/hashCode change is released:
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SpinnakerPluginDescriptor

    return Objects.equals(unsafe, other.unsafe) &&
      Objects.equals(pluginId, other.pluginId) &&
      Objects.equals(pluginDescription, other.pluginDescription) &&
      Objects.equals(pluginClass, other.pluginClass) &&
      Objects.equals(version, other.version) &&
      Objects.equals(requires, other.requires) &&
      Objects.equals(provider, other.provider) &&
      Objects.equals(license, other.license)
  }

  override fun hashCode(): Int {
    return Objects.hash(
      unsafe, pluginId, pluginDescription, pluginClass, version, requires, provider, license)
  }
}
