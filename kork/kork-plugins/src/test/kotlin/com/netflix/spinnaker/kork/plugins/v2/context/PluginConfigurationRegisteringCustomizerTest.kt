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

import com.netflix.spinnaker.kork.plugins.api.PluginConfiguration
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.StandardEnvironment

/**
 * A concrete Plugin subclass in this test package so that
 * [Plugin.basePackageName][com.netflix.spinnaker.kork.plugins.v2.basePackageName]
 * returns this package name, allowing classpath scanning to find [SamplePluginConfig].
 */
@Suppress("DEPRECATION")
internal class ConfigTestPlugin(wrapper: PluginWrapper) : Plugin(wrapper)

/** A sample configuration class that classpath scanning can discover. */
@PluginConfiguration("test")
internal class SamplePluginConfig {
  var value: String = ""
}

@ExtendWith(MockitoExtension::class)
class PluginConfigurationRegisteringCustomizerTest {

  @Mock
  private lateinit var pluginWrapper: PluginWrapper

  @Mock
  private lateinit var configFactory: ConfigFactory

  private fun createPlugin(pluginId: String): Plugin {
    `when`(pluginWrapper.pluginClassLoader).thenReturn(javaClass.classLoader)
    `when`(pluginWrapper.pluginId).thenReturn(pluginId)
    return ConfigTestPlugin(pluginWrapper)
  }

  private fun stubConfigFactory(pluginId: String, config: Any) {
    `when`(configFactory.createPluginConfig(SamplePluginConfig::class.java, pluginId, "test"))
      .thenReturn(config)
  }

  @Test
  fun `accept registers plugin configuration beans`() {
    val pluginId = "test.plugin"
    val plugin = createPlugin(pluginId)
    val config = SamplePluginConfig().apply { value = "hello" }
    stubConfigFactory(pluginId, config)

    val context = GenericApplicationContext().apply { environment = StandardEnvironment() }
    val customizer = PluginConfigurationRegisteringCustomizer(configFactory, pluginWrapper)
    customizer.accept(plugin, context)

    assertThat(context.beanFactory.containsBean("samplePluginConfig")).isTrue()
    assertThat(context.beanFactory.getBean("samplePluginConfig")).isSameAs(config)
  }

  @Test
  fun `accept passes pluginId to configFactory`() {
    val pluginId = "my.custom.plugin"
    val plugin = createPlugin(pluginId)
    val config = SamplePluginConfig()
    stubConfigFactory(pluginId, config)

    val context = GenericApplicationContext().apply { environment = StandardEnvironment() }
    val customizer = PluginConfigurationRegisteringCustomizer(configFactory, pluginWrapper)
    customizer.accept(plugin, context)

    verify(configFactory).createPluginConfig(SamplePluginConfig::class.java, pluginId, "test")
  }

  @Test
  fun `accept uses custom classResolver when provided`() {
    val pluginId = "test.plugin"
    val plugin = createPlugin(pluginId)
    val config = SamplePluginConfig()
    stubConfigFactory(pluginId, config)

    var resolvedClass: String? = null
    val customResolver = object : PluginConfigurationRegisteringCustomizer.ClassResolver {
      override fun resolveClassName(className: String): Class<*> {
        resolvedClass = className
        return Class.forName(className)
      }
    }

    val context = GenericApplicationContext().apply { environment = StandardEnvironment() }
    val customizer = PluginConfigurationRegisteringCustomizer(configFactory, pluginWrapper, customResolver)
    customizer.accept(plugin, context)

    assertThat(resolvedClass).isEqualTo(SamplePluginConfig::class.java.name)
  }

  @Test
  fun `accept handles duplicate bean names`() {
    val pluginId = "test.plugin"
    val plugin = createPlugin(pluginId)
    val config = SamplePluginConfig()
    stubConfigFactory(pluginId, config)

    val context = GenericApplicationContext().apply { environment = StandardEnvironment() }
    // Pre-register a bean with the same name to trigger the duplicate path
    context.beanFactory.registerSingleton("samplePluginConfig", "existingBean")

    val customizer = PluginConfigurationRegisteringCustomizer(configFactory, pluginWrapper)
    customizer.accept(plugin, context)

    // Original bean is still there
    assertThat(context.beanFactory.getBean("samplePluginConfig")).isEqualTo("existingBean")
    // New bean was registered with a suffixed name (contains nanoTime)
    val beanNames = context.beanFactory.singletonNames.toList()
    val suffixedNames = beanNames.filter { it.startsWith("samplePluginConfig") && it != "samplePluginConfig" }
    assertThat(suffixedNames).hasSize(1)
  }
}
