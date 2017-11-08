/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.q

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * An [ApplicationListener] implementation you can use to wait for an execution
 * to complete. Much better than `Thread.sleep(whatever)` in your tests.
 */
class ExecutionLatch(matcher: Matcher<ExecutionComplete>)
  : ApplicationListener<ExecutionComplete> {

  private val predicate = matcher.asPredicate()
  private val latch = CountDownLatch(1)

  override fun onApplicationEvent(event: ExecutionComplete) {
    if (predicate.invoke(event)) {
      latch.countDown()
    }
  }

  fun await() = latch.await(1, TimeUnit.SECONDS)
}

fun ConfigurableApplicationContext.runToCompletion(execution: Execution, launcher: (Execution) -> Unit, repository: ExecutionRepository) {
  val latch = ExecutionLatch(
    has(ExecutionComplete::getExecutionId, equalTo(execution.id))
  )
  addApplicationListener(latch)
  launcher.invoke(execution)
  assert(latch.await()) { "Pipeline did not complete" }

  repository.waitForAllStagesToComplete(execution)
}

fun ConfigurableApplicationContext.restartAndRunToCompletion(stage: Stage, launcher: (Execution, String) -> Unit, repository: ExecutionRepository) {
  val execution = stage.execution
  val latch = ExecutionLatch(
    has(ExecutionComplete::getExecutionId, equalTo(execution.id))
  )
  addApplicationListener(latch)
  launcher.invoke(execution, stage.id)
  assert(latch.await()) { "Pipeline did not complete after restarting" }

  repository.waitForAllStagesToComplete(execution)
}

private fun ExecutionRepository.waitForAllStagesToComplete(execution: Execution) {
  var complete = false
  while (!complete) {
    Thread.sleep(100)
    complete = retrieve(PIPELINE, execution.id)
    .run {
      status.isComplete && stages
        .map(Stage::getStatus)
        .all { it.isComplete || it == NOT_STARTED }
    }
  }
}
