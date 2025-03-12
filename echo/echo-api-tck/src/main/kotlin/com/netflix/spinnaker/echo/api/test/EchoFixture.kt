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

package com.netflix.spinnaker.echo.api.test

import com.netflix.spinnaker.echo.Application
import dev.minutest.TestContextBuilder
import dev.minutest.TestDescriptor
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestContextManager
import org.springframework.test.context.TestPropertySource

/**
 * EchoFixture is the base configuration for an Echo integration test.
 */
@SpringBootTest(classes = [Application::class])
@TestPropertySource(properties = ["spring.config.location=classpath:echo-test-app.yml"])
abstract class EchoFixture

/**
 * DSL for constructing an EchoFixture within a Minutest suite.
 */
inline fun <PF, reified F> TestContextBuilder<PF, F>.echoFixture(
  crossinline factory: (Unit).(testDescriptor: TestDescriptor) -> F
) {
  fixture { testDescriptor ->
    factory(testDescriptor).also {
      TestContextManager(F::class.java).prepareTestInstance(it)
    }
  }
}
