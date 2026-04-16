/*
 * Copyright 2026 Salesforce, Inc.
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
package com.netflix.spinnaker.kork.plugins.v2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.support.GenericApplicationContext

class PluginContainerTest {

  private lateinit var actual: Plugin
  private lateinit var pluginWrapper: PluginWrapper
  private lateinit var serviceContext: GenericApplicationContext
  private lateinit var container: PluginContainer

  companion object {
    private const val PLUGIN_ID = "test.plugin"
  }

  @BeforeEach
  fun setUp() {
    actual = mock(Plugin::class.java)
    pluginWrapper = mock(PluginWrapper::class.java)
    `when`(pluginWrapper.pluginId).thenReturn(PLUGIN_ID)

    serviceContext = GenericApplicationContext()
    container = PluginContainer(actual, serviceContext, pluginWrapper)
  }

  @AfterEach
  fun tearDown() {
    ApplicationContextGraph.pluginContexts.remove(PLUGIN_ID)
    try { serviceContext.close() } catch (_: Exception) {}
  }

  @Test
  fun `pluginContext is registered in ApplicationContextGraph`() {
    assertThat(ApplicationContextGraph.pluginContext(PLUGIN_ID))
      .isNotNull
      .isSameAs(container.pluginContext)
  }

  @Test
  fun `registerInitializer creates bean with correct name`() {
    val registry = mock(BeanDefinitionRegistry::class.java)
    val beanName = container.registerInitializer(registry)

    assertThat(beanName).isEqualTo("${PLUGIN_ID}Initializer")
    verify(registry).registerBeanDefinition(
      org.mockito.ArgumentMatchers.eq("${PLUGIN_ID}Initializer"),
      org.mockito.ArgumentMatchers.any()
    )
  }

  @Test
  fun `registerInitializer passes actual plugin and wrapper as constructor args`() {
    val registry = DefaultListableBeanFactory()
    container.registerInitializer(registry)

    val beanDef = registry.getBeanDefinition("${PLUGIN_ID}Initializer")
    val constructorArgs = beanDef.constructorArgumentValues
    assertThat(constructorArgs.getArgumentValue(0, Plugin::class.java)?.value).isSameAs(actual)
    assertThat(constructorArgs.getArgumentValue(1, PluginWrapper::class.java)?.value).isSameAs(pluginWrapper)
    assertThat(constructorArgs.getArgumentValue(2, GenericApplicationContext::class.java)?.value)
      .isSameAs(container.pluginContext)
  }

  @Test
  fun `start delegates to actual plugin`() {
    container.start()
    verify(actual).start()
  }

  @Test
  fun `stop delegates to actual plugin`() {
    container.stop()
    verify(actual).stop()
  }

  @Test
  fun `delete delegates to actual plugin`() {
    container.delete()
    verify(actual).delete()
  }
}
