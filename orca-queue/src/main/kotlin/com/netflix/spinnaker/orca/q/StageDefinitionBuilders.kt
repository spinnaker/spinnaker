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
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE

/**
 * Build and append the tasks for [stage].
 */
fun StageDefinitionBuilder.buildTasks(stage: Stage<*>) {
  buildTaskGraph(stage)
    .listIterator()
    .forEachWithMetadata { processTaskNode(stage, it) }
}

private fun processTaskNode(
  stage: Stage<*>,
  element: IteratorElement<TaskNode>,
  isSubGraph: Boolean = false
) {
  element.apply {
    when (value) {
      is TaskDefinition -> {
        val task = Task()
        task.id = (stage.getTasks().size + 1).toString()
        task.name = value.name
        task.implementingClass = value.implementingClass.name
        if (isSubGraph) {
          task.isLoopStart = isFirst
          task.isLoopEnd = isLast
        } else {
          task.isStageStart = isFirst
          task.isStageEnd = isLast
        }
        stage.getTasks().add(task)
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
  stage: Stage<out Execution<*>>,
  callback: (Stage<*>) -> Unit = {}
): Unit {
  val executionWindow = stage.buildExecutionWindow()
  syntheticStages(stage).apply {
    buildBeforeStages(stage, executionWindow, callback)
    buildAfterStages(stage, callback)
  }
  buildParallelStages(stage, executionWindow, callback)
}

@Suppress("UNCHECKED_CAST")
private fun StageDefinitionBuilder.parallelStages(stage: Stage<*>) =
  when (stage.getExecution()) {
    is Pipeline -> parallelStages(stage as Stage<Pipeline>)
    is Orchestration -> parallelStages(stage as Stage<Orchestration>)
    else -> throw IllegalStateException()
  }

private typealias SyntheticStages = Map<SyntheticStageOwner, List<Stage<*>>>

@Suppress("UNCHECKED_CAST")
private fun StageDefinitionBuilder.syntheticStages(stage: Stage<out Execution<*>>) =
  when (stage.getExecution()) {
    is Pipeline -> aroundStages(stage as Stage<Pipeline>)
    is Orchestration -> aroundStages(stage as Stage<Orchestration>)
    else -> throw IllegalStateException()
  }
    .groupBy { it.getSyntheticStageOwner()!! }

private fun SyntheticStages.buildBeforeStages(stage: Stage<out Execution<*>>, executionWindow: Stage<out Execution<*>>?, callback: (Stage<*>) -> Unit) {
  val beforeStages = if (executionWindow == null) {
    this[STAGE_BEFORE].orEmpty()
  } else {
    listOf(executionWindow) + this[STAGE_BEFORE].orEmpty()
  }
  beforeStages.forEachIndexed { i, it ->
    it.setRefId("${stage.getRefId()}<${i + 1}")
    if (i > 0) {
      it.setRequisiteStageRefIds(setOf("${stage.getRefId()}<$i"))
    } else {
      it.setRequisiteStageRefIds(emptySet())
    }
    stage.getExecution().apply {
      injectStage(getStages().indexOf(stage), it)
      callback.invoke(it)
    }
  }
}

private fun SyntheticStages.buildAfterStages(stage: Stage<out Execution<*>>, callback: (Stage<*>) -> Unit) {
  val afterStages = this[STAGE_AFTER].orEmpty()
  afterStages.forEachIndexed { i, it ->
    it.setRefId("${stage.getRefId()}>${i + 1}")
    if (i > 0) {
      it.setRequisiteStageRefIds(setOf("${stage.getRefId()}>$i"))
    } else {
      it.setRequisiteStageRefIds(emptySet())
    }
  }
  stage.getExecution().apply {
    val index = getStages().indexOf(stage) + 1
    afterStages.reversed().forEach {
      injectStage(index, it)
      callback.invoke(it)
    }
  }
}

private fun StageDefinitionBuilder.buildParallelStages(stage: Stage<out Execution<*>>, executionWindow: Stage<out Execution<*>>?, callback: (Stage<*>) -> Unit) {
  parallelStages(stage)
    .forEachIndexed { i, it ->
      it.setRefId("${stage.getRefId()}=${i + 1}")
      it.setRequisiteStageRefIds(if (executionWindow == null) emptySet() else setOf(executionWindow.getRefId()))
      stage.getExecution().apply {
        injectStage(getStages().indexOf(stage), it)
        callback.invoke(it)
      }
    }
}

private fun Stage<out Execution<*>>.buildExecutionWindow(): Stage<*>? {
  if (getContext().getOrDefault("restrictExecutionDuringTimeWindow", false) as Boolean) {
    val execution = getExecution()
    val executionWindow = when (execution) {
      is Pipeline -> newStage(
        execution,
        RestrictExecutionDuringTimeWindow.TYPE,
        RestrictExecutionDuringTimeWindow.TYPE,
        getContext().filterKeys { it != "restrictExecutionDuringTimeWindow" },
        this as Stage<Pipeline>,
        STAGE_BEFORE
      )
      is Orchestration -> newStage(
        execution,
        RestrictExecutionDuringTimeWindow.TYPE,
        RestrictExecutionDuringTimeWindow.TYPE,
        getContext().filterKeys { it != "restrictExecutionDuringTimeWindow" },
        this as Stage<Orchestration>,
        STAGE_BEFORE
      )
      else -> throw IllegalStateException()
    }
    executionWindow.setRefId("${getRefId()}<0")
    return executionWindow
  } else {
    return null
  }
}

@Suppress("UNCHECKED_CAST")
private fun Execution<*>.injectStage(index: Int, it: Stage<*>) {
  when (this) {
    is Pipeline -> stages.add(index, it as Stage<Pipeline>)
    is Orchestration -> stages.add(index, it as Stage<Orchestration>)
  }
}
