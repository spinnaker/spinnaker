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
 *
 */
package com.netflix.spinnaker.kork.plugins.v2

import com.netflix.spinnaker.config.PluginsAutoConfiguration
import com.netflix.spinnaker.kork.plugins.api.PluginSdks
import com.netflix.spinnaker.kork.plugins.testplugin.testPlugin
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isTrue

class SpringPluginInitializerTest : JUnit5Minutests {

  fun tests() = rootContext<GeneratedPluginFixture> {
    fixture {
      GeneratedPluginFixture()
    }

    test("all plugin components are present in plugin bean factory") {
      app.run { ctx: AssertableApplicationContext ->
        val pluginContext = ctx.pluginContext(generated.plugin.pluginId)

        expectThat(pluginContext.containsBean("initializerExtension")).isTrue()
        expectThat(pluginContext.containsBean("initializerPluginConfiguration")).isTrue()
        expectThat(pluginContext.containsBean("initializerThing")).isTrue()
      }
    }

    test("plugin configuration is wired up with correct values") {
      app.run { ctx: AssertableApplicationContext ->
        val pluginContext = ctx.pluginContext(generated.plugin.pluginId)

        expectThat(pluginContext.getBean("initializerPluginConfiguration"))
          .isNotNull()
          .and {
            get { javaClass.getDeclaredField("foo").get(this) }.isEqualTo("foo")
          }
      }
    }

    test("plugin sdks is injected into components") {
      app.run { ctx: AssertableApplicationContext ->
        val pluginContext = ctx.pluginContext(generated.plugin.pluginId)

        expectThat(pluginContext.getBean("initializerThing"))
          .isNotNull()
          .and {
            get { javaClass.getDeclaredField("sdks").get(this) }.isA<PluginSdks>()
          }
      }
    }
  }

  private inner class GeneratedPluginFixture {
    val app = ApplicationContextRunner()
      .withPropertyValues(
        "spring.application.name=kork",
        "spinnaker.extensibility.framework.version=V2",
        "spinnaker.extensibility.plugins-root-path=${generated.rootPath.toAbsolutePath()}",
        "spinnaker.extensibility.plugins.${generated.plugin.pluginId}.enabled=true",
        "spinnaker.extensibility.plugins.${generated.plugin.pluginId}.config.foo=foo"
      )
      .withConfiguration(
        AutoConfigurations.of(
          PluginsAutoConfiguration::class.java
        )
      )
  }

  // companion to avoid generating a plugin per test case
  companion object GeneratedPlugin {
    val generated = testPlugin {
      name = "Initializer"

      sourceFile(
        "${name}Extension",
        """
        package $packageName;

        import com.netflix.spinnaker.kork.plugins.testplugin.api.TestExtension;
        import org.pf4j.Extension;

        @Extension
        public class {{simpleName}} implements TestExtension {

          public ${name}PluginConfiguration config;

          public {{simpleName}}(${name}PluginConfiguration config) {
            config = config;
          }

          @Override
          public String getTestValue() {
            return getClass().getSimpleName();
          }
        }
      """.trimIndent()
      )

      sourceFile(
        "${name}PluginConfiguration",
        """
        package $packageName;

        import com.netflix.spinnaker.kork.plugins.api.PluginConfiguration;

        @PluginConfiguration
        public class {{simpleName}} {
          public String foo;
        }
      """.trimIndent()
      )

      sourceFile(
        "${name}Thing",
        """
          package $packageName;

          import com.netflix.spinnaker.kork.plugins.api.PluginComponent;
          import com.netflix.spinnaker.kork.plugins.api.PluginSdks;

          @PluginComponent
          public class {{simpleName}} {
            public PluginSdks sdks;

            public {{simpleName}}(PluginSdks sdks) {
              this.sdks = sdks;
            }
          }
        """.trimIndent()
      )
    }.generate()
  }
}
