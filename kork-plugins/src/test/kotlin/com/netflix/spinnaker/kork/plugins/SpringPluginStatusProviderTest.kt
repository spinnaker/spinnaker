/*
 * Copyright 2019 Netflix, Inc.
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

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class SpringPluginStatusProviderTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("provider attaches itself to environment") {
      val propertySources: MutablePropertySources = mockk(relaxed = true)
      every { environment.propertySources } returns propertySources

      subject.onApplicationEvent(
        ApplicationEnvironmentPreparedEvent(
          mockk(relaxed = true),
          arrayOf(),
          environment
        )
      )

      verify(exactly = 1) { propertySources.addFirst(any<MapPropertySource>()) }
      confirmVerified(propertySources)
    }

    test("plugins default to disabled") {
      every { environment.getProperty(any()) } returnsMany listOf(
        "false",
        "true",
        "false"
      )

      expectThat(subject.isPluginDisabled("hello")).describedAs("initial state").isTrue()
      subject.enablePlugin("hello")
      expectThat(subject.isPluginDisabled("hello")).describedAs("enabled state").isFalse()
      subject.disablePlugin("hello")
      expectThat(subject.isPluginDisabled("hello")).describedAs("disabled state").isTrue()
    }
  }

  private class Fixture {
    val environment: ConfigurableEnvironment = mockk(relaxed = true)
    val subject = SpringPluginStatusProvider(environment)
  }
}
