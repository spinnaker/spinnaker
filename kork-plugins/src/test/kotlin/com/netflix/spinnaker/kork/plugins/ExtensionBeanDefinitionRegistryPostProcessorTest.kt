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
package com.netflix.spinnaker.kork.plugins

import com.netflix.spinnaker.kork.plugins.api.ConfigurableExtension
import com.netflix.spinnaker.kork.plugins.api.SpinnakerExtension
import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin
import com.netflix.spinnaker.kork.plugins.events.ExtensionLoaded
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect
import com.netflix.spinnaker.kork.plugins.update.PluginUpdateService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.pf4j.ExtensionFactory
import org.pf4j.ExtensionPoint
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.support.GenericApplicationContext

class ExtensionBeanDefinitionRegistryPostProcessorTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("post process bean definition registry") {
      test("plugin manager loads and starts plugins") {
        subject.postProcessBeanDefinitionRegistry(GenericApplicationContext())

        verify(exactly = 1) { pluginManager.loadPlugins() }
        verify(exactly = 1) { pluginManager.startPlugins() }
      }

      test("privileged Spring plugins register bean definitions") {
        val plugin: PrivilegedSpringPlugin = mockk(relaxed = true)
        val registry = GenericApplicationContext()
        every { pluginWrapper.plugin } returns plugin
        every { pluginManager.startedPlugins } returns listOf(pluginWrapper)

        subject.postProcessBeanDefinitionRegistry(registry)

        verify(exactly = 1) { plugin.registerBeanDefinitions(registry) }
      }
    }

    context("post process bean factory") {
      test("system extensions are injected into parent Spring registry") {
        every { pluginManager.getExtensionClassNames(null) } returns setOf(
          FooExtension::class.java.name
        )
        every { pluginManager.getExtensionClassNames(eq("testSpringPlugin")) } returns setOf()

        val beanFactory: ConfigurableListableBeanFactory = mockk(relaxed = true)

        subject.postProcessBeanFactory(beanFactory)

        verify(exactly = 1) { extensionFactory.create(eq(FooExtension::class.java)) }
        verify(exactly = 1) { beanFactory.registerSingleton(eq("fooExtensionSystemExtension"), any<FooExtension>()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ExtensionLoaded>()) }
      }

      test("plugin extensions are injected into parent Spring registry") {
        every { pluginManager.getExtensionClassNames(null) } returns setOf()
        every { pluginManager.startedPlugins } returns listOf(pluginWrapper)
        every { pluginManager.getExtensionClassNames(eq("testSpringPlugin")) } returns setOf(
          FooExtension::class.java.name
        )

        val beanFactory: ConfigurableListableBeanFactory = mockk(relaxed = true)

        subject.postProcessBeanFactory(beanFactory)

        verify(exactly = 1) { extensionFactory.create(eq(FooExtension::class.java)) }
        verify(exactly = 1) { beanFactory.registerSingleton(eq("testSpringPluginFooExtension"), any<FooExtension>()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ExtensionLoaded>()) }
      }

      test("unsafe plugin extensions are treated like system extensions") {
        every { pluginDescriptor.unsafe } returns true
        every { pluginManager.getExtensionClassNames(null) } returns setOf(
          FooExtension::class.java.name
        )
        every { pluginManager.startedPlugins } returns listOf(pluginWrapper)
        every { pluginManager.getExtensionClassNames(eq("testSpringPlugin")) } returns setOf(
          FooExtension::class.java.name
        )

        val beanFactory: ConfigurableListableBeanFactory = mockk(relaxed = true)

        subject.postProcessBeanFactory(beanFactory)

        verify(exactly = 1) { extensionFactory.create(eq(FooExtension::class.java)) }
        verify(exactly = 1) { beanFactory.registerSingleton(eq("fooExtensionSystemExtension"), any<FooExtension>()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ExtensionLoaded>()) }
      }
    }
  }

  private class Fixture {
    val pluginManager: SpinnakerPluginManager = mockk(relaxed = true)
    val updateService: PluginUpdateService = mockk(relaxed = true)
    val pluginWrapper: PluginWrapper = mockk(relaxed = true)
    val extensionFactory: ExtensionFactory = mockk(relaxed = true)
    val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    val invocationAspects: List<InvocationAspect<*>> = mockk(relaxed = true)
    val pluginDescriptor: SpinnakerPluginDescriptor = mockk(relaxed = true)

    val subject = ExtensionBeanDefinitionRegistryPostProcessor(pluginManager, updateService,
      applicationEventPublisher, invocationAspects)

    init {
      every { extensionFactory.create(eq(FooExtension::class.java)) } returns FooExtension()
      every { pluginDescriptor.unsafe } returns false
      every { pluginWrapper.pluginClassLoader } returns javaClass.classLoader
      every { pluginWrapper.plugin } returns TestSpringPlugin(pluginWrapper)
      every { pluginWrapper.pluginId } returns "testSpringPlugin"
      every { pluginWrapper.descriptor } returns pluginDescriptor
      every { pluginManager.extensionFactory } returns extensionFactory
    }
  }

  @SpinnakerExtension(id = "netflix.foo")
  private class FooExtension : ExampleExtensionPoint, ConfigurableExtension<FooExtension.FooExtensionConfig> {
    lateinit var config: FooExtensionConfig

    override fun setConfiguration(configuration: FooExtensionConfig) {
      config = configuration
    }

    class FooExtensionConfig
  }

  private interface ExampleExtensionPoint : ExtensionPoint
}
