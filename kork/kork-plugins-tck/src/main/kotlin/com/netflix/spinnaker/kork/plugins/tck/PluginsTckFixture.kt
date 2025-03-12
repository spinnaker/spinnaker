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
 */

package com.netflix.spinnaker.kork.plugins.tck

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.internal.PluginJar
import java.io.File

/**
 * Enforces a set of requirements and conventions that implementors adhere to when testing
 * plugins in Spinnaker services.
 */
interface PluginsTckFixture {

  /**
   * The PF4J [org.pf4j.PluginManager] implementation, used to assert plugin and extension loading.
   */
  val spinnakerPluginManager: SpinnakerPluginManager

  /**
   * The path to write plugins to the filesystem.  The path should match the configuration:
   *
   * <pre>{@code
   * spinnaker:
   *   extensibility:
   *     plugins-root-path: build/plugins
   * }</pre>
   *
   * A relative path will be resolved to an absolute path.  It's a good idea to use the `build`
   * directory in the module under test.
   */
  val plugins: File

  /**
   * A plugin test MUST include one enabled plugin.  Ensure the plugin is enabled in the test
   * configuration:
   *
   * <pre>{@code
   * spinnaker:
   *   extensibility:
   *     plugins-root-path: build/plugins
   *     plugins:
   *       com.netflix.test.plugin:
   *         enabled: true
   * }</pre>
   */
  val enabledPlugin: PluginJar

  /**
   * A plugin test MUST include one disabled plugin.
   *
   * <pre>{@code
   * spinnaker:
   *   extensibility:
   *     plugins-root-path: build/plugins
   *     plugins:
   *       com.netflix.test.plugin:
   *         enabled: false
   * }</pre>
   */
  val disabledPlugin: PluginJar

  /**
   * A plugin test MUST include one plugin that does not meet the system version requirement.  For
   * example, if the system version is 1.0.0 this plugin could have a system version requirement
   * like `>=2.0.0`.
   */
  val versionNotSupportedPlugin: PluginJar

  /**
   * List of extension class names to build with plugin(s).
   */
  val extensionClassNames: MutableList<String>

  /**
   * Build the [PluginJar] using the [PluginJar.Builder].
   *
   * @param pluginId The fully qualified plugin ID
   * @param systemVersionRequirement The system version requirement including the operator, i.e.
   * `>=2.0.0`
   */
  fun buildPlugin(pluginId: String, systemVersionRequirement: String): PluginJar
}
