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

import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskGraph
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.model.Task

/**
 * Build and append the tasks for [stage].
 */
fun StageDefinitionBuilder.buildTasks(stage: Stage) {
  buildTaskGraph(stage)
    .listIterator()
    .forEachWithMetadata { processTaskNode(stage, it) }
}

private fun processTaskNode(
  stage: Stage,
  element: IteratorElement<TaskNode>,
  isSubGraph: Boolean = false
) {
  element.apply {
    when (value) {
      is TaskDefinition -> {
        val task = Task()
        task.id = (stage.tasks.size + 1).toString()
        task.name = value.name
        task.implementingClass = value.implementingClass.name
        if (isSubGraph) {
          task.isLoopStart = isFirst
          task.isLoopEnd = isLast
        } else {
          task.isStageStart = isFirst
          task.isStageEnd = isLast
        }
        stage.tasks.add(task)
      }
      is TaskGraph -> {
        value
          .listIterator()
          .forEachWithMetadata {
            processTaskNode(stage, it, isSubGraph = true)
          }
      }
    }
  }
}

/**
 * Build the synthetic stages for [stage] and inject them into the execution.
 */
fun StageDefinitionBuilder.buildSyntheticStages(
  stage: Stage,
  callback: (Stage) -> Unit = {}
) {
  val executionWindow = stage.buildExecutionWindow()
  syntheticStages(stage).apply {
    buildBeforeStages(stage, executionWindow, this[STAGE_BEFORE].orEmpty(), callback)
    buildAfterStages(stage, this[STAGE_AFTER].orEmpty(), callback)
  }
  buildParallelStages(stage, executionWindow, callback)
}

fun StageDefinitionBuilder.buildAfterStages(
  stage: Stage, afterStages: List<Stage>, callback: (Stage) -> Unit = {}
) {
  afterStages.forEachIndexed { i, it ->
    it.sanitizeContext()
    it.refId = "${stage.refId}>${i + 1}"
    if (i > 0) {
      it.requisiteStageRefIds = setOf("${stage.refId}>$i")
    } else {
      it.requisiteStageRefIds = emptySet()
    }
  }
  stage.execution.apply {
    val index = stages.indexOf(stage) + 1
    afterStages.reversed().forEach {
      injectStage(index, it)
      callback.invoke(it)
    }
  }
}

private fun StageDefinitionBuilder.parallelStages(stage: Stage) =
  parallelStages(stage)

private typealias SyntheticStages = Map<SyntheticStageOwner, List<Stage>>

@Suppress("UNCHECKED_CAST")
private fun StageDefinitionBuilder.syntheticStages(stage: Stage) =
  aroundStages(stage)
    .groupBy { it.syntheticStageOwner!! }

private fun SyntheticStages.buildBeforeStages(stage: Stage,
                                              executionWindow: Stage?,
                                              beforeStages: List<Stage>,
                                              callback: (Stage) -> Unit) {
  val allBeforeStages = if (executionWindow == null) {
    beforeStages
  } else {
    listOf(executionWindow) + beforeStages
  }

  allBeforeStages.forEachIndexed { i, it ->
    it.sanitizeContext()
    it.refId = "${stage.refId}<${i + 1}"
    if (i > 0) {
      it.requisiteStageRefIds = setOf("${stage.refId}<$i")
    } else {
      it.requisiteStageRefIds = emptySet()
    }
    stage.execution.apply {
      injectStage(stages.indexOf(stage), it)
      callback.invoke(it)
    }
  }
}

private fun StageDefinitionBuilder.buildParallelStages(stage: Stage, executionWindow: Stage?, callback: (Stage) -> Unit) {
  parallelStages(stage)
    .forEachIndexed { i, it ->
      it.sanitizeContext()
      it.refId = "${stage.refId}=${i + 1}"
      it.requisiteStageRefIds = if (executionWindow == null) emptySet() else setOf(executionWindow.refId)
      stage.execution.apply {
        injectStage(stages.indexOf(stage), it)
        callback.invoke(it)
      }
    }
}

private fun Stage.buildExecutionWindow(): Stage? {
  if (context.getOrDefault("restrictExecutionDuringTimeWindow", false) as Boolean) {
    val execution = execution
    val executionWindow = newStage(
      execution,
      RestrictExecutionDuringTimeWindow.TYPE,
      RestrictExecutionDuringTimeWindow.TYPE,
      context.filterKeys { it != "restrictExecutionDuringTimeWindow" },
      this,
      STAGE_BEFORE
    )
    executionWindow.refId = "${refId}<0"
    return executionWindow
  } else {
    return null
  }
}

@Suppress("UNCHECKED_CAST")
private fun Execution.injectStage(index: Int, stage: Stage) {
  stages.add(index, stage)
}

private fun Stage.sanitizeContext() {
  if (type != RestrictExecutionDuringTimeWindow.TYPE) {
    context.apply {
      remove("restrictExecutionDuringTimeWindow")
      remove("restrictedExecutionWindow")
    }
  }
}
