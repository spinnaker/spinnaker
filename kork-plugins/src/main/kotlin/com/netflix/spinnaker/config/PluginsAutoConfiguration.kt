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
package com.netflix.spinnaker.config

import com.netflix.spinnaker.kork.plugins.ExtensionsInjector
import com.netflix.spinnaker.kork.plugins.PluginBeanPostProcessor
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import org.pf4j.PluginStatusProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.Environment
import java.nio.file.Paths

/**
 * Include this [Configuration] to enable plugins within a Spinnaker service or library.
 */
@Configuration
@EnableConfigurationProperties(PluginsConfigurationProperties::class)
class PluginsAutoConfiguration {

  @Bean
  fun pluginManager(
    pluginStatusProvider: PluginStatusProvider,
    properties: PluginsConfigurationProperties
  ): SpinnakerPluginManager =
    SpinnakerPluginManager(pluginStatusProvider, Paths.get(properties.rootPath))

  @Bean
  fun extensionsInjector(
    pluginManager: SpinnakerPluginManager,
    context: GenericApplicationContext
  ): ExtensionsInjector =
    ExtensionsInjector(pluginManager, context)

  @Bean
  fun pluginBeanPostProcessor(
    pluginManager: SpinnakerPluginManager,
    extensionsInjector: ExtensionsInjector
  ) =
    PluginBeanPostProcessor(pluginManager, extensionsInjector)

  @Bean
  fun springPluginStatusProvider(environment: Environment): PluginStatusProvider =
    SpringPluginStatusProvider(environment)
}
