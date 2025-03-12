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
package com.netflix.spinnaker.orca.api.test

import com.netflix.spinnaker.orca.Main
import dev.minutest.TestContextBuilder
import dev.minutest.TestDescriptor
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestContextManager
import org.springframework.test.context.TestPropertySource

/**
 * OrcaFixture is the base configuration for an Orca integration test, with all persistence in-memory.
 */
@SpringBootTest(classes = [Main::class])
@ContextConfiguration(classes = [TestKitConfiguration::class])
@TestPropertySource(properties = ["spring.config.location=classpath:orca-test-app.yml"])
abstract class OrcaFixture

/**
 * DSL for constructing an OrcaFixture within a Minutest suite.
 */
inline fun <PF, reified F> TestContextBuilder<PF, F>.orcaFixture(
  crossinline factory: (Unit).(testDescriptor: TestDescriptor) -> F
) {
  fixture { testDescriptor ->
    factory(testDescriptor).also {
      TestContextManager(F::class.java).prepareTestInstance(it)
    }
  }
}
