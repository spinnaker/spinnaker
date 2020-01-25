/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.gradle.extension.extensions

import java.lang.IllegalStateException

/**
 * Configuration for the Spinnaker plugin bundle.
 */
open class SpinnakerBundleExtension {
  /**
   * The Spinnaker plugin ID.
   *
   * Individual plugins contained within the bundle will be assigned a plugin ID derived from this value, following
   * the format "{pluginId}-{serviceName}". For example, if `pluginId` is set to "io.spinnaker.helloworld" and there
   * is an Orca plugin in the bundle, the Orca plugin will be assigned the ID "io.spinnaker.helloworld-orca".
   */
  var pluginId: String
    set(value) {
      _pluginId = value
    }
    get() = requireValue("pluginId", _pluginId)

  private var _pluginId: String? = null

  /**
   * A description of the plugin functionality or behavior.
   */
  var description: String
    set(value) {
      _description = value
    }
    get() = requireValue("description", _description)

  private var _description: String? = null

  /**
   * The version of the plugin bundle.
   *
   * If left unset, the value will be derived from a git tag.
   */
  var version: String
    set(value) {
      _version = value
    }
    get() = requireValue("version", _version)

  private var _version: String? = null

  /**
   * The plugin author.
   */
  var provider: String
    set(value) {
      _provider = value
    }
    get() = requireValue("provider", _provider)

  private var _provider: String? = null

  /**
   * The plugin license.
   */
  var license: String? = null

  private fun requireValue(name: String, value: String?): String {
    if (value == null) {
      throw IllegalStateException("spinnakerBundle.$name must not be null")
    }
    return value
  }
}
