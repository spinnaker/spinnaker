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

package com.netflix.spinnaker.kork.plugins.loaders

import com.netflix.spinnaker.kork.plugins.finders.SpinnakerPropertiesPluginDescriptorFinder
import com.netflix.spinnaker.kork.plugins.testplugin.TestPluginBuilder
import com.netflix.spinnaker.kork.plugins.testplugin.api.TestExtension
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.pf4j.PluginClassLoader
import org.pf4j.PluginDescriptor
import org.pf4j.PluginLoader
import org.pf4j.PluginManager
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

abstract class SpinnakerPluginLoadersTCK : JUnit5Minutests {

  protected abstract fun subjectSupplier(): (f: Fixture) -> PluginLoader

  protected abstract fun buildFixture(): Fixture

  fun tests() = rootContext<Fixture> {
    fixture { buildFixture() }

    test("unsafe plugin directory is applicable") {
      expectThat(subject.isApplicable(unsafePluginPath)).isTrue()
    }

    test("loads the unsafe plugin class") {
      val classpath = subject.loadPlugin(unsafePluginPath, unsafePluginDescriptor)
      expectThat(classpath).isA<UnsafePluginClassLoader>()
      expectThat(classpath.loadClass(unsafePluginDescriptor.pluginClass).name).isEqualTo(unsafePluginDescriptor.pluginClass)
    }

    test("standard plugin directory is applicable") {
      expectThat(subject.isApplicable(standardPluginPath)).isTrue()
    }

    test("loads the standard plugin") {
      val classpath = subject.loadPlugin(standardPluginPath, standardPluginDescriptor)
      expectThat(classpath).isA<PluginClassLoader>()
      val pluginClass = classpath.loadClass(standardPluginDescriptor.pluginClass)
      expectThat(pluginClass.name).isEqualTo(standardPluginDescriptor.pluginClass)
      val extensionClassName = "${pluginClass.`package`.name}.${standardPluginName}TestExtension"
      val extensionClass = classpath.loadClass(extensionClassName)
      val extensionConfigClassName = "${pluginClass.`package`.name}.${standardPluginName}TestExtensionConfiguration"
      val extensionConfigClass = classpath.loadClass(extensionConfigClassName)
      val extensionConfig = extensionConfigClass.newInstance()
      expectThat(TestExtension::class.java.isAssignableFrom(extensionClass)).isTrue()
      val extension = extensionClass.constructors.first().newInstance(extensionConfig) as TestExtension
      expectThat(extension.testValue).isEqualTo(extensionClass.simpleName)
    }
  }

  interface Fixture {
    val subject: PluginLoader
    val unsafePluginPath: Path
    val unsafePluginDescriptor: PluginDescriptor
    val pluginManager: PluginManager
    val standardPluginPath: Path
    val standardPluginDescriptor: PluginDescriptor
    val standardPluginName: String
  }

  protected open inner class FixtureImpl(supplier: (f: Fixture) -> PluginLoader) : Fixture {
    val unsafeDescriptorDirectory: Path = Paths.get(javaClass.getResource("/unsafe-testplugin/plugin.properties").toURI()).parent
    override val unsafePluginPath: Path = unsafeDescriptorDirectory
    override val unsafePluginDescriptor: PluginDescriptor = SpinnakerPropertiesPluginDescriptorFinder().find(unsafeDescriptorDirectory)
    override val pluginManager: PluginManager = mockk(relaxed = true)
    override val standardPluginPath: Path = generatedPluginPath
    override val standardPluginDescriptor: PluginDescriptor = generatedPluginDescriptor
    override val standardPluginName: String = generatedPluginName
    override val subject = supplier(this)
  }

  companion object {
    const val generatedPluginName = "SpinnakerDefaultPluginLoaderTests"
    val generatedPluginPath: Path = Files.createTempDirectory("generatedplugin").also {
      TestPluginBuilder(pluginPath = it, name = generatedPluginName).build()
    }
    val generatedPluginDescriptor: PluginDescriptor = SpinnakerPropertiesPluginDescriptorFinder().find(generatedPluginPath)
  }
}

class SpinnakerDefaultPluginLoaderTest : SpinnakerPluginLoadersTCK() {
  override fun subjectSupplier(): (f: Fixture) -> PluginLoader = { SpinnakerDefaultPluginLoader(it.pluginManager) }
  override fun buildFixture(): Fixture = FixtureImpl(subjectSupplier())
}
