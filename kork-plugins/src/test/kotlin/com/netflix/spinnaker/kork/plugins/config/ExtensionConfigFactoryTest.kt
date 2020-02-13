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

import com.netflix.spinnaker.kork.plugins.api.ConfigurableExtension
import com.netflix.spinnaker.kork.plugins.api.SpinnakerExtension
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class ExtensionConfigFactoryTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("does not support unconfigured extensions") {
      expectThat(subject.supports(UnconfiguredExtension::class.java, MyConfig::class.java)).isFalse()
    }

    test("supports configured extensions") {
      expectThat(subject.supports(MyExtension::class.java, MyConfig::class.java)).isTrue()
    }

    test("creates a config") {
      every {
        configResolver.resolve(any<SystemExtensionConfigCoordinates>(), eq(MyConfig::class.java))
      } returns MyConfig("yes, hello")

      expectThat(subject.provide(MyExtension::class.java, null))
        .isA<MyConfig>()
        .get { message }.isEqualTo("yes, hello")
    }
  }

  private inner class Fixture {
    val configResolver: ConfigResolver = mockk()
    val subject = ExtensionConfigFactory(configResolver)
  }

  private data class MyConfig(val message: String)

  @SpinnakerExtension(id = "my")
  private class MyExtension : ConfigurableExtension<MyConfig> {
    override fun setConfiguration(configuration: MyConfig?) {
      throw UnsupportedOperationException("not implemented")
    }
  }

  @SpinnakerExtension(id = "unconfigured")
  private class UnconfiguredExtension
}
