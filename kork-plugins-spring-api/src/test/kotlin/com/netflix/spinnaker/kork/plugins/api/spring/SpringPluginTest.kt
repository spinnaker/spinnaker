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
package com.netflix.spinnaker.kork.plugins.api.spring

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class SpringPluginTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("should close application context on stop") {
      plugin.stop()
      verify(exactly = 1) { applicationContext.stop() }
    }
  }

  private inner class Fixture {
    val applicationContext: AnnotationConfigApplicationContext = mockk(relaxed = true)
    val plugin: SpringPlugin = TestSpringPlugin(mockk(relaxed = true)).also {
      it.applicationContext = applicationContext
    }
  }
}
