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

/**
 * Configuration for backend service modules.
 */
open class SpinnakerPluginExtension {

  /**
   * The Spinnaker service name that this plugin is for.
   */
  var serviceName: String?
    set(value) {
      _serviceName = value
    }
    get() = requireValue("serviceName", _serviceName)

  private var _serviceName: String? = null

  /**
   * The fully qualified plugin class name.
   */
  var pluginClass: String?
    set(value) {
      _pluginClass = value
    }
    get() = requireValue("pluginClass", _pluginClass)

  private var _pluginClass: String? = null

  /**
   * Service version requirements for the plugin, e.g. "orca>=7.0.0".
   *
   * If this value remains unset, the value will be `$serviceName>=0.0.0`, effectively matching any version of the
   * target Spinnaker service.
   */
  var requires: String?
    set(value) {
      _requires = value
    }
    get() {
      return _requires ?: "$serviceName>=0.0.0"
    }

  private var _requires: String? = null

  /**
   * Not yet supported.
   */
  var dependencies: String? = null

  private fun requireValue(name: String, value: String?): String {
    if (value == null) {
      throw IllegalStateException("spinnakerPlugin.$name must not be null")
    }
    return value
  }
}
