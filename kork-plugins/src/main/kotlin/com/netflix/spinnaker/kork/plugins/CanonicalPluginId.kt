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
import java.util.regex.Pattern

/**
 * Representation of the Spinnaker canonical plugin ID format.
 *
 * The namespace is dual-purposed: It allows multiple organizations to have the same plugin ID, but not clash in
 * an installation. It can also be used for authorization for plugin publishing, writes and so-on.
 *
 * For example, official Spinnaker OSS plugins might have an `io.spinnaker` namespace, and authz would require the
 * ADMIN role to write to. Alternatively, `com.netflix.streaming.platform.delivery-engineering` would require a user
 * to be in the Delivery Engineering team. This mapping of namespace to authorization would need to be enabled via
 * configuration.
 *
 * @param namespace An organizationally-unique namespace.
 * @param id The plugin ID. This must be unique within a namespace.
 */
data class CanonicalPluginId(
  val namespace: String,
  val id: String
) {

  companion object {
    private val pattern = Pattern.compile("^(?<namespace>[\\w\\-.])+\\.(?<id>[\\w\\-]+)$")

    /**
     * Returns a boolean of whether or not the given [pluginId] is correctly formed.
     */
    fun isValid(pluginId: String): Boolean =
      pattern.matcher(pluginId).matches()

    /**
     * Validates the given [pluginId], throwing an exception if the ID is malformed.
     */
    fun validate(pluginId: String) {
      if (!isValid(pluginId)) {
        throw MalformedPluginIdException(pluginId)
      }
    }

    /**
     * Parses the given [pluginId] into a [CanonicalPluginId] model.
     */
    fun parse(pluginId: String): CanonicalPluginId? {
      val matcher = pattern.matcher(pluginId)
      if (matcher.matches()) {
        return CanonicalPluginId(matcher.group("namespace"), matcher.group("id"))
      }
      return null
    }
  }

  internal class MalformedPluginIdException(
    providedId: String
  ) : IntegrationException(
    "Plugin '$providedId' does not conform to Spinnaker's Canonical Plugin ID. " +
      "Canonical IDs must follow a '{namespace}.{pluginId}' format ($pattern)."
  )
}
