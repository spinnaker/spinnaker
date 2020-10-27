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
import com.netflix.spinnaker.kork.plugins.testplugin.testPlugin
import com.netflix.spinnaker.kork.plugins.v2.enablePlugin
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isSuccess
import javax.annotation.PostConstruct

/**
 * Demonstrates that a plugin that injects a service bean can also be injected into a different service bean.
 * This is a combination of [ServiceDependenciesScenarioTest] and [ServiceInjectionScenarioTest], but tested simultaneously.
 *
 * The dependency graph looks like this:
 * ParentServiceBean (service) -> ComplexInjectionExtension (plugin) -> InjectsPluginExtensions (service)
 * */
class ComplexInjectionScenarioTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("bi-directional (service <-> plugin) injection works") {
      app.run { ctx ->
        val injectsPluginExtensions = ctx.getBean(InjectsPluginExtensions::class.java)
        expectThat(injectsPluginExtensions.testExtensions) {
          // Plugin bean has been injected into service bean.
          // i.e., ComplexInjectionExtension -> InjectsPluginExtensions
          isNotEmpty()
          get { first() }
            .isA<TestExtension>()
            .get { extensionClass.simpleName }.isEqualTo("ComplexInjectionExtension")

          // Service bean has been injected into plugin bean.
          // i.e., ParentServiceBean -> ComplexInjectionExtension
          // The generated test extension (defined below) uses "getTestValue()" to return the class name
          // of the service bean that it injected.
          get { first().testValue }.isEqualTo("ParentServiceBean")
        }
      }
    }
  }

  private class Fixture {
    val plugin = testPlugin {
      sourceFile(
        "ComplexInjectionExtension",
        """
          package {{basePackageName}};

          import com.netflix.spinnaker.kork.plugins.v2.scenarios.fixtures.ParentServiceBean;
          import com.netflix.spinnaker.kork.plugins.testplugin.api.TestExtension;

          public class {{simpleName}} implements TestExtension {

            public ParentServiceBean parentServiceBean;

            public {{simpleName}}(ParentServiceBean parentBean) {
              this.parentServiceBean = parentBean;
            }

            @Override
            public String getTestValue() {
              return parentServiceBean.getClass().getSimpleName();
            }
          }
        """.trimIndent()
      )
    }.generate()

    val app = ApplicationContextRunner()
      .withPropertyValues(
        "spring.application.name=kork",
        "spinnaker.extensibility.framework.version=$FRAMEWORK_V2",
        "spinnaker.extensibility.plugins-root-path=${plugin.rootPath.toAbsolutePath()}"
      )
      .enablePlugin(plugin.descriptor.pluginId)
      .withUserConfiguration(ComplexInjectionTestConfiguration::class.java)
      .withConfiguration(
        AutoConfigurations.of(
          PluginsAutoConfiguration::class.java
        )
      )
  }

  @TestConfiguration
  @ComponentScan("com.netflix.spinnaker.kork.plugins.v2.scenarios.fixtures")
  private class ComplexInjectionTestConfiguration {
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
        expectCatching { it.testValue }.isSuccess().isEqualTo("ParentServiceBean")
      }
    }
  }
}
