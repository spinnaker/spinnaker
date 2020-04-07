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
 *
 */
package com.netflix.spinnaker.clouddriver.saga.examples

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.clouddriver.saga.AbstractSagaEvent
import com.netflix.spinnaker.clouddriver.saga.AbstractSagaTest
import com.netflix.spinnaker.clouddriver.saga.ManyCommands
import com.netflix.spinnaker.clouddriver.saga.SagaCommand
import com.netflix.spinnaker.clouddriver.saga.SagaCommandCompleted
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction
import com.netflix.spinnaker.clouddriver.saga.flow.SagaCompletionHandler
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import dev.minutest.rootContext
import java.util.function.Predicate
import strikt.api.expectThat
import strikt.assertions.isEqualTo

/**
 * This example shows how to do branching logic inside of Sagas.
 */
class BranchingExampleTest : AbstractSagaTest() {
  fun tests() = rootContext<Fixture> {

    context("branching logic") {
      fixture { Fixture() }

      val flow = SagaFlow()
        .then(PrepareAction::class.java)
        .then(TheThingAction::class.java)
        .on(ShouldDoOptionalThings::class.java) {
          it.then(AnOptionalThingAction::class.java)
        }
        .then(FinishAction::class.java)
        .completionHandler(ThingsCompletedHandler::class.java)

      test("branch skipped") {
        expectThat(sagaService.applyBlocking<String>("test", "test", flow, PrepareForThings(false)))
          .isEqualTo("not branch")
      }

      test("branch entered") {
        expectThat(sagaService.applyBlocking<String>("test", "test", flow, PrepareForThings(true)))
          .isEqualTo("branch")
      }
    }
  }

  private inner class Fixture : BaseSagaFixture() {
    init {
      registerBeans(
        applicationContext,
        ThingsCompletedHandler::class.java,
        PrepareAction::class.java,
        TheThingAction::class.java,
        AnOptionalThingAction::class.java,
        FinishAction::class.java,
        ShouldDoOptionalThings::class.java
      )
    }
  }

  @JsonTypeName("prepareForThings")
  class PrepareForThings(val doOptionalThings: Boolean) : AbstractSagaEvent(), SagaCommand

  @JsonTypeName("doTheThing")
  class DoTheThing : AbstractSagaEvent(), SagaCommand

  @JsonTypeName("doAnOptionalThing")
  class DoAnOptionalThing : AbstractSagaEvent(), SagaCommand

  @JsonTypeName("finishThings")
  class FinishThings : AbstractSagaEvent(), SagaCommand

  class PrepareAction : SagaAction<PrepareForThings> {
    override fun apply(command: PrepareForThings, saga: Saga): SagaAction.Result {
      return SagaAction.Result(DoTheThing())
    }
  }

  class TheThingAction : SagaAction<DoTheThing> {
    override fun apply(command: DoTheThing, saga: Saga): SagaAction.Result {
      // TODO(rz): Add a condition predicate that just checks for whether or not the command exists at all instead?
      return SagaAction.Result(
        ManyCommands(
          DoAnOptionalThing(),
          FinishThings()
        )
      )
    }
  }

  class AnOptionalThingAction : SagaAction<DoAnOptionalThing> {
    override fun apply(command: DoAnOptionalThing, saga: Saga): SagaAction.Result {
      return SagaAction.Result()
    }
  }

  internal class FinishAction : SagaAction<FinishThings> {
    override fun apply(command: FinishThings, saga: Saga): SagaAction.Result {
      return SagaAction.Result()
    }
  }

  internal class ShouldDoOptionalThings : Predicate<Saga> {
    override fun test(t: Saga): Boolean = t.getEvents().filterIsInstance<PrepareForThings>().first().doOptionalThings
  }

  internal class ThingsCompletedHandler : SagaCompletionHandler<String> {
    override fun handle(completedSaga: Saga): String? {
      val optionalThingApplied = completedSaga
        .getEvents()
        .filterIsInstance<SagaCommandCompleted>()
        .any { it.matches(DoAnOptionalThing::class.java) }
      return if (optionalThingApplied) "branch" else "not branch"
    }
  }
}
