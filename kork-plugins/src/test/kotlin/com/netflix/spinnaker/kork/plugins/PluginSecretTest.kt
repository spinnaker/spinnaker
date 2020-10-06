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

package com.netflix.spinnaker.kork.plugins

import com.netflix.spinnaker.config.PluginsAutoConfiguration
import com.netflix.spinnaker.kork.plugins.testplugin.api.TestExtension
import com.netflix.spinnaker.kork.plugins.testplugin.testPlugin
import com.netflix.spinnaker.kork.secrets.EncryptedSecret
import com.netflix.spinnaker.kork.secrets.SecretConfiguration
import com.netflix.spinnaker.kork.secrets.SecretEngine
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import strikt.api.expectThat
import strikt.assertions.isEqualTo

/**
 * Demonstrates that Spinnaker secrets can be resolved within @PluginConfiguration-annotated classes in the v2 framework.
 * */
class PluginSecretTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("secrets can be decrypted") {
      app.run { ctx ->
        val extension = ctx.getBeansOfType(TestExtension::class.java).entries.first().value
        expectThat(extension.testValue).isEqualTo("decrypted")
      }
    }
  }

  private class Fixture {
    val plugin = testPlugin {
      name = "PluginSecretTest"

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
            this.config = config;
          }

          @Override
          public String getTestValue() {
            return config.foo;
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
    }.generate()

    val app = ApplicationContextRunner()
      .withPropertyValues(
        "spring.application.name=kork",
        "spinnaker.extensibility.framework.version=v2",
        "spinnaker.extensibility.plugins-root-path=${plugin.rootPath.toAbsolutePath()}",
        "spinnaker.extensibility.plugins.${plugin.descriptor.pluginId}.enabled=true",
        "spinnaker.extensibility.plugins.spinnaker.generated-testplugin.config.foo=encrypted:${TestSecretEngineConfiguration.ENGINE}!key:encrypted"
      )
      .withUserConfiguration(
        SecretConfiguration::class.java,
        TestSecretEngineConfiguration::class.java
      )
      .withConfiguration(
        AutoConfigurations.of(
          PluginsAutoConfiguration::class.java
        )
      )
  }

  @TestConfiguration
  private class TestSecretEngineConfiguration {
    @Bean
    fun testSecretEngine(): SecretEngine = object : SecretEngine {
      override fun clearCache() = Unit
      override fun decrypt(encryptedSecret: EncryptedSecret) = "decrypted".toByteArray()
      override fun identifier() = ENGINE
      override fun validate(encryptedSecret: EncryptedSecret) = Unit
    }

    companion object {
      const val ENGINE = "test-engine"
    }
  }
}

