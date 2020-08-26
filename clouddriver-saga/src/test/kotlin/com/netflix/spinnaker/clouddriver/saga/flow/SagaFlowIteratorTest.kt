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
package com.netflix.spinnaker.clouddriver.saga.flow

import com.netflix.spinnaker.clouddriver.saga.AbstractSagaTest
import com.netflix.spinnaker.clouddriver.saga.Action1
import com.netflix.spinnaker.clouddriver.saga.Action2
import com.netflix.spinnaker.clouddriver.saga.Action3
import com.netflix.spinnaker.clouddriver.saga.DoAction1
import com.netflix.spinnaker.clouddriver.saga.DoAction2
import com.netflix.spinnaker.clouddriver.saga.DoAction3
import com.netflix.spinnaker.clouddriver.saga.SagaCommandCompleted
import com.netflix.spinnaker.clouddriver.saga.SagaCommandSkipped
import com.netflix.spinnaker.clouddriver.saga.SagaConditionEvaluated
import com.netflix.spinnaker.clouddriver.saga.ShouldBranch
import com.netflix.spinnaker.clouddriver.saga.ShouldBranchPredicate
import dev.minutest.rootContext
import strikt.api.expect
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class SagaFlowIteratorTest : AbstractSagaTest() {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("iterates top-level actions only") {
      expect {
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action1>()
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action3>()
        that(subject.hasNext()).isFalse()
      }
    }

    test("iterates conditional actions") {
      saga.addEventForTest(ShouldBranch())

      expect {
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action1>()
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action2>()
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action3>()
        that(subject.hasNext()).isFalse()
      }
    }

    test("conditions are not re-evaluated") {
      saga.addEventForTest(SagaConditionEvaluated("shouldBranch", true))

      expect {
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action1>()
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action2>()
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action3>()
        that(subject.hasNext()).isFalse()
      }
    }

    test("seeks iterator with partially applied saga") {
      saga.addEventForTest(SagaCommandCompleted("doAction1"))
      saga.addEventForTest(ShouldBranch())

      expect {
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action2>()
        that(subject.next()).get { action }.isA<Action3>()
        that(subject.hasNext()).isFalse()
      }
    }

    test("handles ManyCommands completed out-of-order") {
      saga.addEventForTest(DoAction1())
      saga.addEventForTest(SagaCommandCompleted("doAction1"))
      saga.addEventForTest(ShouldBranch())
      saga.addEventForTest(DoAction2())
      saga.addEventForTest(DoAction3())

      expect {
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action2>()
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action3>()
        that(subject.hasNext()).isFalse()
      }
    }

    test("adds skipped command messages for skipped branches") {
      saga.addEventForTest(DoAction1())
      saga.addEventForTest(SagaCommandCompleted("doAction1"))
      saga.addEventForTest(DoAction2())
      saga.addEventForTest(DoAction3())

      expect {
        that(subject.hasNext()).isTrue()
        that(subject.next()).get { action }.isA<Action3>()
        that(subject.hasNext()).isFalse()

        // The iterator doesn't perform saves, so the skip events will be pending.
        that(saga.getPendingEvents().filterIsInstance<SagaCommandSkipped>()).and {
          hasSize(1)
          first().get { command }.isEqualTo("doAction2")
        }
      }
    }
  }

  private inner class Fixture : BaseSagaFixture() {
    val flow = SagaFlow()
      .then(Action1::class.java)
      .on(ShouldBranchPredicate::class.java) {
        it.then(Action2::class.java)
      }
      .then(Action3::class.java)

    val subject = SagaFlowIterator(sagaRepository, applicationContext, saga, flow)

    init {
      sagaRepository.save(saga)
    }
  }
}
