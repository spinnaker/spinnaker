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
package com.netflix.spinnaker.kork.plugins.config

import com.netflix.spinnaker.kork.plugins.api.ExtensionConfiguration
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class ConfigFactoryTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("creates an extension config") {
      every {
        configResolver.resolve(any<ExtensionConfigCoordinates>(), eq(MyExtensionConfig::class.java))
      } returns MyExtensionConfig("yes, hello")

      expectThat(subject.createExtensionConfig(MyExtensionConfig::class.java, "my-sweet-extension", "my-plugin"))
        .isA<MyExtensionConfig>()
        .get { message }.isEqualTo("yes, hello")
    }

    test("creates a plugin config") {
      every {
        configResolver.resolve(any<PluginConfigCoordinates>(), eq(MyPluginConfig::class.java))
      } returns MyPluginConfig("yes, hello")

      expectThat(subject.createPluginConfig(MyPluginConfig::class.java, "my-plugin"))
        .isA<MyPluginConfig>()
        .get { message }.isEqualTo("yes, hello")
    }
  }

  private inner class Fixture {
    val configResolver: ConfigResolver = mockk()
    val subject = ConfigFactory(configResolver)
  }

  @ExtensionConfiguration("my-sweet-extension")
  private data class MyExtensionConfig(val message: String)

  private data class MyPluginConfig(val message: String)
}
