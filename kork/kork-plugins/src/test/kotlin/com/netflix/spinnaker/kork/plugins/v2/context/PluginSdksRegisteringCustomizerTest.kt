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
package com.netflix.spinnaker.kork.plugins.v2.context

import com.netflix.spinnaker.kork.plugins.sdk.PluginSdksImpl
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ConfigurableApplicationContext

@ExtendWith(MockitoExtension::class)
class PluginSdksRegisteringCustomizerTest {

  @Mock
  private lateinit var plugin: Plugin

  @Mock
  private lateinit var pluginWrapper: PluginWrapper

  @Mock
  private lateinit var sdkFactory: SdkFactory

  @Mock
  private lateinit var serviceApplicationContext: ConfigurableApplicationContext

  @Mock
  private lateinit var pluginContext: ConfigurableApplicationContext

  @Mock
  private lateinit var pluginBeanFactory: ConfigurableListableBeanFactory

  @Test
  fun `accept registers pluginSdks bean into context`() {
    `when`(serviceApplicationContext.getBeansOfType(SdkFactory::class.java))
      .thenReturn(mapOf("sdkFactory" to sdkFactory))
    `when`(sdkFactory.create(plugin.javaClass, pluginWrapper)).thenReturn("sdk")
    `when`(pluginContext.beanFactory).thenReturn(pluginBeanFactory)

    val customizer = PluginSdksRegisteringCustomizer(serviceApplicationContext, pluginWrapper)
    customizer.accept(plugin, pluginContext)

    val beanCaptor = ArgumentCaptor.forClass(Any::class.java)
    verify(pluginBeanFactory).registerSingleton(eq("pluginSdks"), beanCaptor.capture())
    assertThat(beanCaptor.value).isInstanceOf(PluginSdksImpl::class.java)
  }

  @Test
  fun `accept passes plugin class and wrapper to each SdkFactory`() {
    `when`(serviceApplicationContext.getBeansOfType(SdkFactory::class.java))
      .thenReturn(mapOf("sdkFactory" to sdkFactory))
    `when`(sdkFactory.create(plugin.javaClass, pluginWrapper)).thenReturn("sdk")
    `when`(pluginContext.beanFactory).thenReturn(pluginBeanFactory)

    val customizer = PluginSdksRegisteringCustomizer(serviceApplicationContext, pluginWrapper)
    customizer.accept(plugin, pluginContext)

    verify(sdkFactory).create(plugin.javaClass, pluginWrapper)
  }

  @Test
  fun `accept works with no SdkFactory beans`() {
    `when`(serviceApplicationContext.getBeansOfType(SdkFactory::class.java))
      .thenReturn(emptyMap())
    `when`(pluginContext.beanFactory).thenReturn(pluginBeanFactory)

    val customizer = PluginSdksRegisteringCustomizer(serviceApplicationContext, pluginWrapper)
    customizer.accept(plugin, pluginContext)

    val beanCaptor = ArgumentCaptor.forClass(Any::class.java)
    verify(pluginBeanFactory).registerSingleton(eq("pluginSdks"), beanCaptor.capture())
    assertThat(beanCaptor.value).isInstanceOf(PluginSdksImpl::class.java)
  }
}
