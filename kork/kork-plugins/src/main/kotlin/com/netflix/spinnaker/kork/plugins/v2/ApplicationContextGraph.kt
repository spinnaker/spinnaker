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
package com.netflix.spinnaker.kork.plugins.v2

import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext

/**
 * For testing purposes only. Spring doesn't have a way to find child contexts from a parent.
 */
internal object ApplicationContextGraph {
  lateinit var serviceApplicationContext: ConfigurableApplicationContext
  val pluginContexts: MutableMap<String, ConfigurableApplicationContext> = mutableMapOf()

  fun pluginContext(pluginId: String): ConfigurableApplicationContext? =
    pluginContexts[pluginId]
}
