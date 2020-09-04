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
 */

package com.netflix.spinnaker.kork.plugins.tck

import dev.minutest.ContextBuilder
import dev.minutest.TestContextBuilder
import dev.minutest.TestDescriptor
import dev.minutest.junit.JUnit5Minutests
import org.pf4j.PluginWrapper
import org.springframework.test.context.TestContextManager
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isGreaterThanOrEqualTo
import strikt.assertions.isNull
import strikt.assertions.isTrue

/**
 * The default tests that run in a plugin test suite.
 */
abstract class PluginsTck<T : PluginsTckFixture> : JUnit5Minutests {

  /**
   * Basic tests that assert the plugin framework is behaving its fundamental plugin loading responsibilities within
   * a service.
   */
  fun ContextBuilder<T>.defaultPluginTests() {
    context("an integration test environment and relevant plugins") {

      test("the enabled plugin starts with the expected extensions loaded") {
        expect {
          that(spinnakerPluginManager.startedPlugins.size).isGreaterThanOrEqualTo(1)
          that(spinnakerPluginManager.startedPlugins.find { it.pluginId == enabledPlugin.pluginId }).isA<PluginWrapper>()
          spinnakerPluginManager.getExtensionClasses(enabledPlugin.pluginId).let { extensionClasses ->
            extensionClasses.forEach { extensionClass ->
              that(extensionClassNames.contains(extensionClass.name)).isTrue()
            }
          }
        }
      }

      test("the disabled plugin is not loaded") {
        expect {
          that(spinnakerPluginManager.startedPlugins.find { it.pluginId == disabledPlugin.pluginId }).isNull()
        }
      }

      test("the plugin with the incompatible system version requirement is not loaded") {
        expect {
          that(spinnakerPluginManager.startedPlugins.find { it.pluginId == versionNotSupportedPlugin.pluginId }).isNull()
        }
      }
    }
  }
}

/**
 * DSL for constructing a service fixture within a Minutest suite.
 */
inline fun <PF, reified F> TestContextBuilder<PF, F>.serviceFixture(
  crossinline factory: (Unit).(testDescriptor: TestDescriptor) -> F
) {
  fixture { testDescriptor ->
    factory(testDescriptor).also {
      TestContextManager(F::class.java).prepareTestInstance(it)
    }
  }
}
