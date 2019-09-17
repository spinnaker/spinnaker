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
package com.netflix.spinnaker.clouddriver.saga.flow

import com.netflix.spinnaker.clouddriver.saga.AbstractSagaEvent
import com.netflix.spinnaker.clouddriver.saga.Action1
import com.netflix.spinnaker.clouddriver.saga.Action2
import com.netflix.spinnaker.clouddriver.saga.Action3
import com.netflix.spinnaker.clouddriver.saga.SagaCommand
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow.InjectLocation.AFTER
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow.InjectLocation.BEFORE
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.hasSize

class SagaFlowTest : JUnit5Minutests {

  fun tests() = rootContext {
    context("inject") {

      test("add step before") {
        val flow = SagaFlow()
          .then(Action1::class.java)
          .then(Action2::class.java)
          .then(Action3::class.java)

        flow.inject(BEFORE, Action2::class.java, Action4::class.java)

        expectThat(flow.steps)
          .hasSize(4)
          .get { filterIsInstance<SagaFlow.ActionStep>().map { it.action } }
          .containsExactly(
            Action1::class.java,
            Action4::class.java,
            Action2::class.java,
            Action3::class.java
          )
      }

      test("add step after") {
        val flow = SagaFlow()
          .then(Action1::class.java)
          .then(Action2::class.java)
          .then(Action3::class.java)

        flow.inject(AFTER, Action2::class.java, Action4::class.java)

        expectThat(flow.steps)
          .hasSize(4)
          .get { filterIsInstance<SagaFlow.ActionStep>().map { it.action } }
          .containsExactly(
            Action1::class.java,
            Action2::class.java,
            Action4::class.java,
            Action3::class.java
          )
      }

      test("add step after last") {
        val flow = SagaFlow()
          .then(Action1::class.java)
          .then(Action2::class.java)
          .then(Action3::class.java)

        flow.inject(AFTER, Action3::class.java, Action4::class.java)

        expectThat(flow.steps)
          .hasSize(4)
          .get { filterIsInstance<SagaFlow.ActionStep>().map { it.action } }
          .containsExactly(
            Action1::class.java,
            Action2::class.java,
            Action3::class.java,
            Action4::class.java
          )
      }

      test("add step first") {
        val flow = SagaFlow()
          .then(Action1::class.java)
          .then(Action2::class.java)
          .then(Action3::class.java)

        flow.injectFirst(Action4::class.java)

        expectThat(flow.steps)
          .hasSize(4)
          .get { filterIsInstance<SagaFlow.ActionStep>().map { it.action } }
          .containsExactly(
            Action4::class.java,
            Action1::class.java,
            Action2::class.java,
            Action3::class.java
          )
      }
    }
  }

  class DoAction4 : AbstractSagaEvent(), SagaCommand

  private inner class Action4 : SagaAction<DoAction4> {
    override fun apply(command: DoAction4, saga: Saga): SagaAction.Result {
      throw UnsupportedOperationException("not implemented")
    }
  }
}
