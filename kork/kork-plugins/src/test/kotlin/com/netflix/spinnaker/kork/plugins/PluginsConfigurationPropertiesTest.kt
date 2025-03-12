/*
 * Copyright 2021 Armory, Inc.
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

import com.netflix.spinnaker.config.PluginsAutoConfiguration
import com.netflix.spinnaker.config.PluginsConfigurationProperties
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.doesNotContain
import strikt.assertions.isEqualTo
import java.nio.file.Paths

class PluginsConfigurationPropertiesTest : JUnit5Minutests {
  fun tests() = rootContext {
    derivedContext<ApplicationContextRunner>("when default plugin repositories are enabled (true by default)") {
      fixture {
        ApplicationContextRunner()
          .withPropertyValues(
            "spring.application.name=kork",
          )
          .withConfiguration(
            AutoConfigurations.of(
              PluginsAutoConfiguration::class.java
            )
          )
      }

      test("default repositories should have been configured") {
        run { ctx ->
          val updateManager = ctx.getBean(SpinnakerUpdateManager::class.java)
          expectThat(updateManager.repositories.map { it.id }).contains(
            PluginsConfigurationProperties.SPINNAKER_OFFICIAL_REPOSITORY,
            PluginsConfigurationProperties.SPINNAKER_COMMUNITY_REPOSITORY,
          )
        }
      }
    }

    derivedContext<ApplicationContextRunner>("when default plugin repositories are disabled") {
      fixture {
        ApplicationContextRunner()
          .withPropertyValues(
            "spring.application.name=kork",
            "spinnaker.extensibility.enable-default-repositories=false"
          )
          .withConfiguration(
            AutoConfigurations.of(
              PluginsAutoConfiguration::class.java
            )
          )
      }

      test("default repositories should not been configured") {
        run { ctx ->
          val updateManager = ctx.getBean(SpinnakerUpdateManager::class.java)
          expectThat(updateManager.repositories.map { it.id }).doesNotContain(
            PluginsConfigurationProperties.SPINNAKER_OFFICIAL_REPOSITORY,
            PluginsConfigurationProperties.SPINNAKER_COMMUNITY_REPOSITORY,
          )
        }
      }
    }

    derivedContext<ApplicationContextRunner>("when no plugins root path is configured") {
      fixture {
        ApplicationContextRunner()
          .withPropertyValues(
            "spring.application.name=kork",
          )
          .withConfiguration(
            AutoConfigurations.of(
              PluginsAutoConfiguration::class.java
            )
          )
      }

      test("plugins root path is 'plugins'") {
        run { ctx ->
          val pluginManager = ctx.getBean(SpinnakerPluginManager::class.java)
          expectThat(pluginManager.pluginsRoot).isEqualTo(Paths.get(PluginsConfigurationProperties.DEFAULT_ROOT_PATH))
        }
      }
    }

    derivedContext<ApplicationContextRunner>("when a plugins root path is configured") {
      fixture {
        ApplicationContextRunner()
          .withPropertyValues(
            "spring.application.name=kork",
            "spinnaker.extensibility.plugins-root-path=path/to/my/plugins"
          )
          .withConfiguration(
            AutoConfigurations.of(
              PluginsAutoConfiguration::class.java
            )
          )
      }

      test("plugins root path is absolute path of configured value") {
        run { ctx ->
          val pluginManager = ctx.getBean(SpinnakerPluginManager::class.java)
          expectThat(pluginManager.pluginsRoot).isEqualTo(Paths.get("path/to/my/plugins").toAbsolutePath())
        }
      }
    }
  }
}
