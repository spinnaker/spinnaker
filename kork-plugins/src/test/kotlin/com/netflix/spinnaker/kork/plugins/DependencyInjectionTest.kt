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

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.api.ExtensionConfiguration
import com.netflix.spinnaker.kork.plugins.api.PluginConfiguration
import com.netflix.spinnaker.kork.plugins.api.PluginSdks
import com.netflix.spinnaker.kork.plugins.api.yaml.YamlResourceLoader
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory
import com.netflix.spinnaker.kork.plugins.config.ConfigResolver
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import com.netflix.spinnaker.kork.plugins.sdk.yaml.YamlResourceLoaderSdkFactory
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import java.lang.IllegalStateException
import java.nio.file.Paths
import org.pf4j.ExtensionPoint
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class DependencyInjectionTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    context("extension injection") {
      fixture { ExtensionFixture() }

      context("system extensions") {
        test("without config") {
          every { pluginManager.whichPlugin(any()) } returns null
          expectThat(subject.create(NoConfigSystemExtension::class.java))
            .isA<NoConfigSystemExtension>()
        }

        test("with config") {
          every { pluginManager.whichPlugin(any()) } returns null

          val config = TheConfig()
          every { configResolver.resolve(any(), any<Class<TheConfig>>()) } returns config

          expectThat(subject.create(ConfiguredSystemExtension::class.java))
            .isA<ConfiguredSystemExtension>().get { config }
            .isEqualTo(config)
        }
      }

      context("plugin extensions") {
        before {
          every { pluginManager.whichPlugin(any()) } returns pluginWrapper
          every { pluginWrapper.descriptor } returns createPluginDescriptor("pluginz.bestone")
        }

        test("without config") {
          expectThat(subject.create(MyPlugin.NoConfigExtension::class.java))
            .isA<MyPlugin.NoConfigExtension>()
        }

        test("with config") {
          val config = MyPlugin.ConfiguredExtension.TheConfig()
          every { configResolver.resolve(any(), any<Class<MyPlugin.ConfiguredExtension.TheConfig>>()) } returns config

          expectThat(subject.create(MyPlugin.ConfiguredExtension::class.java))
            .isA<MyPlugin.ConfiguredExtension>()
            .get { config }.isEqualTo(config)
        }

        test("with unsupported constructor argument") {
          expectThrows<IntegrationException> {
            subject.create(MyPlugin.UnsupportedArgumentExtension::class.java)
          }
        }

        test("with multiple constructors") {
          expectThrows<IntegrationException> {
            subject.create(MyPlugin.MultipleConstructorsExtension::class.java)
          }
        }

        test("with sdks") {
          expectThat(subject.create(MyPlugin.SdkAwareExtension::class.java))
            .isA<MyPlugin.SdkAwareExtension>()
            .get { sdks }
            .isA<PluginSdks>().and {
              get { yamlResourceLoader() }.isA<YamlResourceLoader>()
            }
        }
      }
    }

    context("plugins") {
      fixture { PluginFixture() }

      test("without injection") {
        val pluginWrapper = PluginWrapper(
          pluginManager,
          SpinnakerPluginDescriptor(pluginId = "hello", pluginClass = PluginWithoutInjection::class.java.canonicalName),
          Paths.get("/dev/null"),
          javaClass.classLoader
        )

        expectThat(subject.create(pluginWrapper))
          .isA<PluginWithoutInjection>()
      }

      test("with config") {
        val pluginWrapper = PluginWrapper(
          pluginManager,
          SpinnakerPluginDescriptor(pluginId = "hello", pluginClass = PluginWithConfig::class.java.canonicalName),
          Paths.get("/dev/null"),
          javaClass.classLoader
        )

        val config = PluginConfig()
        every { configResolver.resolve(any(), any<Class<PluginConfig>>()) } returns config

        expectThat(subject.create(pluginWrapper))
          .isA<PluginWithConfig>()
          .get { config }.isEqualTo(config)
      }

      test("with unsupported constructor argument") {
        val pluginWrapper = PluginWrapper(
          pluginManager,
          SpinnakerPluginDescriptor(pluginId = "hello", pluginClass = PluginWithUnsupportedArg::class.java.canonicalName),
          Paths.get("/dev/null"),
          javaClass.classLoader
        )

        expectThrows<IntegrationException> {
          subject.create(pluginWrapper)
        }
      }

      test("with multiple constructors") {
        val pluginWrapper = PluginWrapper(
          pluginManager,
          SpinnakerPluginDescriptor(pluginId = "hello", pluginClass = PluginWithMultipleConstructors::class.java.canonicalName),
          Paths.get("/dev/null"),
          javaClass.classLoader
        )

        expectThrows<IntegrationException> {
          subject.create(pluginWrapper)
        }
      }

      test("with sdks") {
        val pluginWrapper = PluginWrapper(
          pluginManager,
          SpinnakerPluginDescriptor(pluginId = "hello", pluginClass = PluginWithSdks::class.java.canonicalName),
          Paths.get("/dev/null"),
          javaClass.classLoader
        )

        expectThat(subject.create(pluginWrapper))
          .isA<PluginWithSdks>()
          .get { sdks }
          .isA<PluginSdks>()
      }
    }
  }

  private abstract inner class Fixture {
    val configResolver: ConfigResolver = mockk(relaxed = true)
    val pluginManager: SpinnakerPluginManager = mockk(relaxed = true)
    val configFactory: ConfigFactory = ConfigFactory(configResolver)
    val sdkFactories: List<SdkFactory> = listOf(YamlResourceLoaderSdkFactory())
    val pluginWrapper: PluginWrapper = mockk(relaxed = true)

    abstract val subject: FactoryDelegate

    inner class FactoryDelegate(val factory: Any) {
      fun create(arg: Any): Any? =
        when (factory) {
          is SpinnakerPluginFactory -> factory.create(arg as PluginWrapper)
          is SpinnakerExtensionFactory -> factory.create(arg as Class<*>)
          else -> throw IllegalStateException(
            "Factory must be either SpinnakerPluginFactory or SpinnakerExtensionFactory"
          )
        }
    }
  }

  private inner class ExtensionFixture : Fixture() {
    override val subject = FactoryDelegate(SpinnakerExtensionFactory(pluginManager, configFactory, sdkFactories))
  }

  private inner class PluginFixture : Fixture() {
    override val subject = FactoryDelegate(SpinnakerPluginFactory(sdkFactories, configFactory))
  }

  private fun createPluginDescriptor(pluginId: String): SpinnakerPluginDescriptor {
    val descriptor: SpinnakerPluginDescriptor = mockk(relaxed = true)
    every { descriptor.pluginId } returns pluginId
    return descriptor
  }

  private interface TheExtensionPoint : ExtensionPoint

  private class NoConfigSystemExtension : TheExtensionPoint

  @ExtensionConfiguration("extension-point-configuration")
  class TheConfig

  @PluginConfiguration
  class PluginConfig

  private class ConfiguredSystemExtension(
    private val config: TheConfig
  ) : TheExtensionPoint

  class MyPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    class NoConfigExtension : TheExtensionPoint

    class ConfiguredExtension(
      private val config: TheConfig
    ) : TheExtensionPoint {

      @ExtensionConfiguration("extension-point-configuration")
      class TheConfig
    }

    class UnsupportedArgumentExtension(
      private val bad: String
    ) : TheExtensionPoint

    class MultipleConstructorsExtension(
      private val validConfig: ValidConfig
    ) : TheExtensionPoint {

      constructor(bad: String, validConfig: ValidConfig) : this(validConfig)

      @ExtensionConfiguration("valid-config")
      class ValidConfig
    }

    class SdkAwareExtension(
      val sdks: PluginSdks
    ) : TheExtensionPoint
  }
}

internal class PluginWithoutInjection(wrapper: PluginWrapper) : Plugin(wrapper)
internal class PluginWithSdks(wrapper: PluginWrapper, val sdks: PluginSdks) : Plugin(wrapper)
internal class PluginWithConfig(wrapper: PluginWrapper, val config: DependencyInjectionTest.PluginConfig) : Plugin(wrapper)
internal class PluginWithUnsupportedArg(wrapper: PluginWrapper, val bad: String) : Plugin(wrapper)
internal class PluginWithMultipleConstructors(wrapper: PluginWrapper) : Plugin(wrapper) {
  constructor(wrapper: PluginWrapper, sdks: PluginSdks) : this(wrapper)
}
