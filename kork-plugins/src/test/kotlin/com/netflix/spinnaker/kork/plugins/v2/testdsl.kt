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
 *
 */
package com.netflix.spinnaker.kork.plugins.v2

import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ConfigurableApplicationContext
import java.lang.IllegalStateException

fun AssertableApplicationContext.pluginContext(pluginId: String): ConfigurableApplicationContext =
  ApplicationContextGraph.pluginContext(pluginId)
    ?: throw IllegalStateException("No plugin context found for plugin '$pluginId'")

fun ApplicationContextRunner.enablePlugin(pluginId: String): ApplicationContextRunner =
  withPropertyValues("spinnaker.extensibility.plugins.$pluginId.enabled=true")
