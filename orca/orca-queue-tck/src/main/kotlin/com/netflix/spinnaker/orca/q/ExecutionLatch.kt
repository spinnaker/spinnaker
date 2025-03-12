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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext

/**
 * An [ApplicationListener] implementation you can use to wait for an execution
 * to complete. Much better than `Thread.sleep(whatever)` in your tests.
 */
class ExecutionLatch(private val predicate: Predicate<ExecutionComplete>) :
  ApplicationListener<ExecutionComplete> {

  private val latch = CountDownLatch(1)

  override fun onApplicationEvent(event: ExecutionComplete) {
    if (predicate.test(event)) {
      latch.countDown()
    }
  }

  fun await() = latch.await(10, TimeUnit.SECONDS)
}

fun ConfigurableApplicationContext.run(execution: PipelineExecution, launcher: (PipelineExecution) -> Unit) {
  val latch = ExecutionLatch(
    Predicate {
      it.executionId == execution.id
    }
  )
  addApplicationListener(latch)
  launcher.invoke(execution)

  Thread.sleep(500)
}

fun ConfigurableApplicationContext.runToCompletion(execution: PipelineExecution, launcher: (PipelineExecution) -> Unit, repository: ExecutionRepository) {
  val latch = ExecutionLatch(
    Predicate {
      it.executionId == execution.id
    }
  )
  addApplicationListener(latch)
  launcher.invoke(execution)
  assert(latch.await()) { "Pipeline did not complete" }

  repository.waitForAllStagesToComplete(execution)
}

/**
 * Given parent and child pipelines:
 * 1) Start child pipeline
 * 2) Invoke parent pipeline and continue until completion
 *
 * Useful for testing failure interactions between pipelines. Child pipeline can be inspected prior to
 * completion, and subsequently completed via [runToCompletion].
 */
fun ConfigurableApplicationContext.runParentToCompletion(
  parent: PipelineExecution,
  child: PipelineExecution,
  launcher: (PipelineExecution) -> Unit,
  repository: ExecutionRepository
) {
  val latch = ExecutionLatch(
    Predicate {
      it.executionId == parent.id
    }
  )

  addApplicationListener(latch)
  launcher.invoke(child)
  launcher.invoke(parent)
  assert(latch.await()) { "Pipeline did not complete" }

  repository.waitForAllStagesToComplete(parent)
}

fun ConfigurableApplicationContext.restartAndRunToCompletion(stage: StageExecution, launcher: (PipelineExecution, String) -> Unit, repository: ExecutionRepository) {
  val execution = stage.execution
  val latch = ExecutionLatch(
    Predicate {
      it.executionId == execution.id
    }
  )
  addApplicationListener(latch)
  launcher.invoke(execution, stage.id)
  assert(latch.await()) { "Pipeline did not complete after restarting" }

  repository.waitForAllStagesToComplete(execution)
}

private fun ExecutionRepository.waitForAllStagesToComplete(execution: PipelineExecution) {
  var complete = false
  while (!complete) {
    Thread.sleep(100)
    complete = retrieve(PIPELINE, execution.id)
      .run {
        status.isComplete && stages
          .map(StageExecution::getStatus)
          .all { it.isComplete || it == NOT_STARTED }
      }
  }
}
