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
 *
 */

package com.netflix.spinnaker.kork.plugins.v2.scenarios

import com.netflix.spinnaker.config.PluginsAutoConfiguration
import com.netflix.spinnaker.kork.plugins.FRAMEWORK_V2
import com.netflix.spinnaker.kork.plugins.testplugin.api.TestExtension
import com.netflix.spinnaker.kork.plugins.testplugin.basicGeneratedPlugin
import com.netflix.spinnaker.kork.plugins.v2.enablePlugin
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import strikt.api.expectCatching
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import javax.annotation.PostConstruct

/**
 * Tests that service beans can inject beans provided by plugins.
 * */
class ServiceInjectionScenarioTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("a service-level bean can inject a plugin extension") {
      app.run { ctx ->
        val injectsPluginExtensions = ctx.getBean(InjectsPluginExtensions::class.java)

        val testExtension = injectsPluginExtensions.testExtensions.first()
        expectCatching { testExtension.testValue }
          .isSuccess()
          .isEqualTo("ServiceInjectionTestExtension") // The generated test extension returns its own class name from the "getTestValue" method.
      }
    }
  }

  private class Fixture {
    val plugin = basicGeneratedPlugin("ServiceInjectionTest").generate()
    val app = ApplicationContextRunner()
      .withPropertyValues(
        "spring.application.name=kork",
        "spinnaker.extensibility.framework.version=$FRAMEWORK_V2",
        "spinnaker.extensibility.plugins-root-path=${plugin.rootPath.toAbsolutePath()}",
      )
      .enablePlugin(plugin.descriptor.pluginId)
      .withUserConfiguration(ServiceInjectionTestConfiguration::class.java)
      .withConfiguration(
        AutoConfigurations.of(
          PluginsAutoConfiguration::class.java
        )
      )
  }

  @TestConfiguration
  private class ServiceInjectionTestConfiguration {
    @Bean
    fun injectsPluginExtensions(testExtensions: List<TestExtension>): InjectsPluginExtensions {
      return InjectsPluginExtensions(testExtensions)
    }
  }

  private class InjectsPluginExtensions(val testExtensions: List<TestExtension>) {
    @PostConstruct
    fun tryOutExtensions() {
      // Verify that extensions can be used immediately.
      testExtensions.forEach {
        expectCatching { it.testValue }
          .isSuccess()
          .isEqualTo("ServiceInjectionTestExtension")
      }
    }
  }
}

