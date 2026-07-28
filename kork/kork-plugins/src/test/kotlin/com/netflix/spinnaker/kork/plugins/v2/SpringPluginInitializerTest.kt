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
import org.assertj.core.api.Assertions.assertThat

class SpringPluginInitializerTest : JUnit5Minutests {

  fun tests() = rootContext<GeneratedPluginFixture> {
    fixture {
      GeneratedPluginFixture()
    }

    test("all plugin components are present in plugin bean factory") {
      app.run { ctx: AssertableApplicationContext ->
        val pluginContext = ctx.pluginContext(generated.plugin.pluginId)

        assertThat(pluginContext.containsBean("initializerExtension")).isTrue()
        assertThat(pluginContext.containsBean("initializerPluginConfiguration")).isTrue()
        assertThat(pluginContext.containsBean("initializerThing")).isTrue()
      }
    }

    test("plugin configuration is wired up with correct values") {
      app.run { ctx: AssertableApplicationContext ->
        val pluginContext = ctx.pluginContext(generated.plugin.pluginId)

        val configBean = pluginContext.getBean("initializerPluginConfiguration")
        assertThat(configBean.javaClass.getDeclaredField("foo").get(configBean))
          .isEqualTo("foo")
      }
    }

    test("plugin sdks is injected into components") {
      app.run { ctx: AssertableApplicationContext ->
        val pluginContext = ctx.pluginContext(generated.plugin.pluginId)

        val thingBean = pluginContext.getBean("initializerThing")
        assertThat(thingBean.javaClass.getDeclaredField("sdks").get(thingBean))
          .isInstanceOf(PluginSdks::class.java)
      }
    }

    test("wrapper-derived identifiers propagate correctly") {
      app.run { ctx: AssertableApplicationContext ->
        val pluginId = generated.plugin.pluginId

        // PluginContainer registers the plugin context using wrapper.pluginId
        assertThat(ApplicationContextGraph.pluginContext(pluginId)).isNotNull()

        // PluginContainer.registerInitializer names the bean using wrapper.pluginId
        assertThat(ctx.containsBean("${pluginId}Initializer")).isTrue()

        // SpinnakerPluginService.registerProxies names proxy beans using wrapper.descriptor.pluginId
        val proxyBeanNames = ctx.sourceApplicationContext.getBeanNamesForType(
          com.netflix.spinnaker.kork.plugins.testplugin.api.TestExtension::class.java
        )
        assertThat(proxyBeanNames.any { name -> name.startsWith(pluginId) }).isTrue()
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
