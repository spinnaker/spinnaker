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

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import java.util.function.Predicate
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.core.annotation.Order

@JsonTypeName("shouldBranch")
class ShouldBranch : AbstractSagaEvent()

@JsonTypeName("doAction1")
class DoAction1(
  val branch: Boolean = true
) : AbstractSagaEvent(), SagaCommand

@JsonTypeName("doAction2")
class DoAction2 : AbstractSagaEvent(), SagaCommand

@JsonTypeName("doAction3")
class DoAction3 : AbstractSagaEvent(), SagaCommand

class Action1 : SagaAction<DoAction1> {
  override fun apply(command: DoAction1, saga: Saga): SagaAction.Result {
    val events = if (command.branch) listOf(ShouldBranch()) else listOf()
    return SagaAction.Result(
      ManyCommands(
        DoAction2(),
        DoAction3()
      ),
      events
    )
  }
}

@Order(HIGHEST_PRECEDENCE)
class Action2 : SagaAction<DoAction2> {
  override fun apply(command: DoAction2, saga: Saga): SagaAction.Result {
    return SagaAction.Result(null, listOf())
  }
}

@Order(HIGHEST_PRECEDENCE)
class Action3 : SagaAction<DoAction3> {
  override fun apply(command: DoAction3, saga: Saga): SagaAction.Result {
    return SagaAction.Result(null, listOf())
  }
}

@Order(HIGHEST_PRECEDENCE)
class ShouldBranchPredicate : Predicate<Saga> {
  override fun test(t: Saga): Boolean =
    t.getEvents().filterIsInstance<ShouldBranch>().isNotEmpty()
}
