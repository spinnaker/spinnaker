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
package com.netflix.spinnaker.kork.plugins.sdk.serde

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext

/**
 * Builds a [SerdeServiceImpl] for plugins to use.
 *
 * This will search the service [ApplicationContext] for a single [ObjectMapper]. If more than one [ObjectMapper]
 * is found, the first one will be chosen. This is an unsafe operation, as there are no guarantees that the same
 * [ObjectMapper] will be chosen on each startup - a log error is raised so that service developers can track these
 * issues down and consolidate where possible.
 */
class SerdeServiceSdkFactory(
  private val applicationContext: ApplicationContext
) : SdkFactory {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val serdeService by lazy {
    applicationContext.getBeansOfType(ObjectMapper::class.java)
      .let {
        if (it.isEmpty()) {
          // This should never happen. Obviously it probably will at some point, but it's better to explode than
          // provide a "reasonable default" since that seems to change depending on who you ask. Better to ensure
          // a service has wired up an ObjectMapper to matches what it has defined as reasonable defaults.
          throw SystemException("Failed to locate ObjectMapper in application context")
        } else {
          val first = it.entries.first()
          if (it.size > 1) {
            // This is an indication that the service has multiple object mappers available, which will introduce
            // uncertainty in exactly how objects will be serialization/deserialized. We should be converging on one
            // ObjectMapper instance in the ApplicationContext in services, so this is really up to service maintainers
            // to resolve if it occurs, but it isn't the end of the world since most of our ObjectMappers are
            // configured roughly the same.
            val options = it.keys.joinToString()
            log.warn("Found more than one ObjectMapper ($options), selecting '${first.key}'")
          }
          first.value
        }
      }
      .let { SerdeServiceImpl(it) }
  }

  override fun create(pluginClass: Class<*>, pluginWrapper: PluginWrapper?): Any =
    serdeService
}
