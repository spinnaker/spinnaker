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

import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow
import com.netflix.spinnaker.clouddriver.saga.models.Saga
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

    context("re-entrance") {
      fixture {
        ReentranceFixture()
      }

      mapOf(
        "completed doAction1" to listOf(
          DoAction1(),
          SagaCommandCompleted("doAction1"),
          DoAction2()
        ),
        "completed doAction1, doAction2 incomplete" to listOf(
          DoAction1(),
          SagaCommandCompleted("doAction1"),
          DoAction2(),
          SagaCommandCompleted("doAction2 incomplete")
        )
      ).forEach { (name, previousEvents) ->
        test("a saga resumes where it left off: $name") {
          val flow = SagaFlow()
            .then(ReentrantAction1::class.java)
            .then(ReentrantAction2::class.java)
            .then(ReentrantAction3::class.java)

          // We've already done some of the work.
          sagaRepository.save(saga, previousEvents)

          // Apply the saga "again"
          sagaService.applyBlocking<String>("test", "test", flow, DoAction1())

          val saga = sagaRepository.get("test", "test")
          expectThat(saga)
            .describedAs(name)
            .isNotNull()
            .and {
              get { getEvents() }.filterIsInstance<SagaCommand>().map { it.javaClass.simpleName }.containsExactly(
                "DoAction1",
                "DoAction2",
                "DoAction3"
              )
              if (name.equals("completed doAction1")) {
                get { getEvents() }.filterIsInstance<SagaCommandCompleted>().map { it.command }.containsExactly(
                  "doAction1",
                  "doAction2",
                  "doAction3"
                )
              } else {
                get { getEvents() }.filterIsInstance<SagaCommandCompleted>().map { it.command }.containsExactly(
                  "doAction1",
                  "doAction2 incomplete",
                  "doAction2",
                  "doAction3"
                )
              }
              get { getEvents() }.map { it.javaClass }.contains(SagaCompleted::class.java)
            }
        }
      }
    }
  }

  private inner class ReentranceFixture : BaseSagaFixture() {
    init {
      registerBeans(
        applicationContext,
        ReentrantAction1::class.java,
        ReentrantAction2::class.java,
        ReentrantAction3::class.java
      )
    }
  }
}

private class ReentrantAction1 : SagaAction<DoAction1> {
  override fun apply(command: DoAction1, saga: Saga): SagaAction.Result {
    return SagaAction.Result(DoAction2())
  }
}

private class ReentrantAction2 : SagaAction<DoAction2> {
  override fun apply(command: DoAction2, saga: Saga): SagaAction.Result {
    return SagaAction.Result(DoAction3())
  }
}

private class ReentrantAction3 : SagaAction<DoAction3> {
  override fun apply(command: DoAction3, saga: Saga): SagaAction.Result {
    return SagaAction.Result()
  }
}
