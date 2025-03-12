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
package com.netflix.spinnaker.kork.plugins.v2.context

import com.netflix.spinnaker.kork.plugins.sdk.PluginSdksImpl
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import org.pf4j.Plugin
import org.springframework.context.ConfigurableApplicationContext

/**
 * Registers [PluginSdks] into a [Plugin] application context.
 *
 * V1 API compatibility. We could get rid of the PluginSdks concept entirely now and just inject interfaces for
 * the SDKs we want to expose, rather than proxying them through [PluginSdksImpl].
 */
class PluginSdksRegisteringCustomizer(
  private val serviceApplicationContext: ConfigurableApplicationContext
) : PluginApplicationContextCustomizer {

  override fun accept(plugin: Plugin, context: ConfigurableApplicationContext) {
    val sdk = PluginSdksImpl(
      serviceApplicationContext.getBeansOfType(SdkFactory::class.java).values
        .map { it.create(plugin.javaClass, plugin.wrapper) }
    )
    context.beanFactory.registerSingleton("pluginSdks", sdk)
  }
}
