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

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import org.pf4j.PluginDescriptor
import java.util.regex.Pattern

/**
 * Validates that a given [PluginDescriptor] plugin ID conforms to the Spinnaker canonical plugin ID format.
 */
object CanonicalPluginIdValidator {

  private val pattern = Pattern.compile("^[\\w\\-]+\\.[\\w\\-]+$")

  fun validate(pluginDescriptor: PluginDescriptor) {
    if (!pattern.matcher(pluginDescriptor.pluginId).matches()) {
      throw MalformedPluginIdException(pluginDescriptor.pluginId)
    }
  }

  private class MalformedPluginIdException(
    providedId: String
  ) : IntegrationException(
    "Plugin '$providedId' does not conform to Spinnaker's Canonical Plugin ID. " +
      "Canonical IDs must follow a '{namespace}.{pluginId}' format ($pattern)."
  )
}
