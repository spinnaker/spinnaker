/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.kork.plugins.api.spring

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class SpringLoaderPluginTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("should register SpringLoader") {
      plugin.registerBeanDefinitions(registry)
      verify(exactly = 1) { registry.registerBeanDefinition(
        "plugin1.com.netflix.spinnaker.kork.plugins.api.spring.SpringLoader",
        BeanDefinitionBuilder.genericBeanDefinition(SpringLoader::class.java)
          .setScope(BeanDefinition.SCOPE_SINGLETON)
          .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_NO)
          .addConstructorArgValue(plugin.pluginContext)
          .addConstructorArgValue(javaClass.classLoader)
          .addConstructorArgValue(listOf("io.armory.plugin.example.spring"))
          .addConstructorArgValue(listOf(TestSpringLoaderPlugin.MyService::class.java))
          .getBeanDefinition()
      )}
    }

    test("should delay requestMappingHandlerMapping") {
      val mappingBeanDefinition = GenericBeanDefinition()
      every { registry.getBeanDefinition("requestMappingHandlerMapping") } returns mappingBeanDefinition

      plugin.registerBeanDefinitions(registry)

      Assertions.assertThat(mappingBeanDefinition.dependsOn)
        .isEqualTo(listOf("plugin1.com.netflix.spinnaker.kork.plugins.api.spring.SpringLoader").toTypedArray())
    }

  }

  private inner class Fixture {
    val registry: BeanDefinitionRegistry = mockk(relaxed = true)
    val pluginWrapper: PluginWrapper = mockk(relaxed = true)
    val plugin: SpringLoaderPlugin = TestSpringLoaderPlugin(pluginWrapper)
    init {
      every { pluginWrapper.pluginId } returns "plugin1"
    }
  }
}
