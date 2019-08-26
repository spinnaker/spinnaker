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
package com.netflix.spinnaker.clouddriver.saga

import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.filterIsInstance
import strikt.assertions.isNotNull
import strikt.assertions.map

class SagaServiceTest : AbstractSagaTest() {

  fun tests() = rootContext<BaseSagaFixture> {
    fixture {
      BaseSagaFixture()
    }

    context("a simple saga") {
      val flow = SagaFlow()
        .then(Action1::class.java)
        .then(Action2::class.java)
        .then(Action3::class.java)

      test("applies all commands") {
        sagaService.applyBlocking<String>("test", "test", flow, DoAction1())

        expectThat(sagaRepository.get("test", "test"))
          .isNotNull()
          .and {
            get { getEvents() }.filterIsInstance<SagaCommand>().map { it.javaClass.simpleName }.containsExactly(
              "DoAction1",
              "DoAction2",
              "DoAction3"
            )
            get { getEvents() }.map { it.javaClass }.contains(SagaCompleted::class.java)
          }
      }
    }
  }
}
