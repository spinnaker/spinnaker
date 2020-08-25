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
package com.netflix.spinnaker.config

import com.netflix.spinnaker.kork.plugins.FRAMEWORK_V2
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import com.netflix.spinnaker.kork.plugins.update.release.provider.PluginInfoReleaseProvider
import com.netflix.spinnaker.kork.plugins.v2.SpringPluginFactory
import com.netflix.spinnaker.kork.plugins.v2.PluginFrameworkInitializer
import com.netflix.spinnaker.kork.plugins.v2.SpinnakerPluginService
import org.pf4j.PluginFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.GenericApplicationContext

@Configuration
@ConditionalOnProperty(value = ["spinnaker.extensibility.framework.version"], havingValue = FRAMEWORK_V2)
class V2PluginConfiguration {

  @Bean
  fun pluginFactory(
    sdkFactories: List<SdkFactory>,
    configFactory: ConfigFactory,
    applicationContext: GenericApplicationContext,
    invocationAspects: List<InvocationAspect<*>>
  ): PluginFactory =
    SpringPluginFactory(sdkFactories, configFactory, applicationContext, invocationAspects)

  @Bean
  fun pluginService(
    pluginManager: SpinnakerPluginManager,
    updateManager: SpinnakerUpdateManager,
    pluginInfoReleaseProvider: PluginInfoReleaseProvider,
    springPluginStatusProvider: SpringPluginStatusProvider,
    applicationEventPublisher: ApplicationEventPublisher
  ): SpinnakerPluginService =
    SpinnakerPluginService(
      pluginManager,
      updateManager,
      pluginInfoReleaseProvider,
      springPluginStatusProvider
    )

  @Bean
  fun pluginPostProcessor(pluginService: SpinnakerPluginService): PluginFrameworkInitializer =
    PluginFrameworkInitializer(pluginService)
}
