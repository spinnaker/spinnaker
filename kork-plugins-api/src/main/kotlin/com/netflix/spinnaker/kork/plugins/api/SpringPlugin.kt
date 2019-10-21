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
package com.netflix.spinnaker.kork.plugins.api

import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext

/**
 * Allows a plugin to use its own Spring [ApplicationContext].
 *
 * The [ApplicationContext] created by a plugin extending this class will also have its [ApplicationContext]
 * auto-configured with a subset of standard core configuration properties & beans.
 */
abstract class SpringPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
  lateinit var applicationContext: AnnotationConfigApplicationContext

  abstract fun initApplicationContext()

  override fun stop() {
    applicationContext.close()
  }
}
