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

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository

/**
 * Messages used internally by the queueing system.
 */
@JsonTypeInfo(use = MINIMAL_CLASS, include = PROPERTY, property = "@class")
sealed class Message

interface ApplicationAware {
  val application: String
}

interface ExecutionLevel : ApplicationAware {
  val executionType: Class<out Execution<*>>
  val executionId: String
}

interface StageLevel : ExecutionLevel {
  val stageId: String
}

interface TaskLevel : StageLevel {
  val taskId: String
}

data class StartTask(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String,
  override val taskId: String
) : Message(), TaskLevel {
  constructor(source: ExecutionLevel, stageId: String, taskId: String) :
    this(source.executionType, source.executionId, source.application, stageId, taskId)

  constructor(source: StageLevel, taskId: String) :
    this(source, source.stageId, taskId)

  constructor(source: Stage<*>, taskId: String) :
    this(source.getExecution().javaClass, source.getExecution().getId(), source.getExecution().getApplication(), source.getId(), taskId)

  constructor(source: Stage<*>, task: com.netflix.spinnaker.orca.pipeline.model.Task) :
    this(source.getExecution().javaClass, source.getExecution().getId(), source.getExecution().getApplication(), source.getId(), task.id)
}

data class CompleteTask(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String,
  override val taskId: String,
  val status: ExecutionStatus
) : Message(), TaskLevel {
  constructor(source: TaskLevel, status: ExecutionStatus) :
    this(source.executionType, source.executionId, source.application, source.stageId, source.taskId, status)
}

data class PauseTask(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String,
  override val taskId: String
) : Message(), TaskLevel {
  constructor(message: TaskLevel) :
    this(message.executionType, message.executionId, message.application, message.stageId, message.taskId)
}

data class ResumeTask(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String,
  override val taskId: String
) : Message(), TaskLevel {
  constructor(message: StageLevel, taskId: String) :
    this(message.executionType, message.executionId, message.application, message.stageId, taskId)
}

data class RunTask(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String,
  override val taskId: String,
  val taskType: Class<out Task>
) : Message(), TaskLevel {
  constructor(message: StageLevel, taskId: String, taskType: Class<out Task>) :
    this(message.executionType, message.executionId, message.application, message.stageId, taskId, taskType)

  constructor(message: TaskLevel, taskType: Class<out Task>) :
    this(message.executionType, message.executionId, message.application, message.stageId, message.taskId, taskType)
}

data class StartStage(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String
) : Message(), StageLevel {
  constructor(source: ExecutionLevel, stageId: String) :
    this(source.executionType, source.executionId, source.application, stageId)

  constructor(source: StageLevel) :
    this(source, source.stageId)

  constructor(source: Stage<*>) :
    this(source.getExecution().javaClass, source.getExecution().getId(), source.getExecution().getApplication(), source.getId())
}

data class ContinueParentStage(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String
) : Message(), StageLevel {
  constructor(source: Stage<*>) :
    this(source.getExecution().javaClass, source.getExecution().getId(), source.getExecution().getApplication(), source.getId())
}

data class CompleteStage(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String,
  val status: ExecutionStatus
) : Message(), StageLevel {
  constructor(source: ExecutionLevel, stageId: String, status: ExecutionStatus) :
    this(source.executionType, source.executionId, source.application, stageId, status)

  constructor(source: StageLevel, status: ExecutionStatus) :
    this(source, source.stageId, status)

  constructor(source: Stage<*>, status: ExecutionStatus) :
    this(source.getExecution().javaClass, source.getExecution().getId(), source.getExecution().getApplication(), source.getId(), status)
}

data class PauseStage(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String
) : Message(), StageLevel {
  constructor(source: StageLevel) :
    this(source, source.stageId)

  constructor(source: ExecutionLevel, stageId: String) :
    this(source.executionType, source.executionId, source.application, stageId)
}

data class RestartStage(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String,
  val user: String?
) : Message(), StageLevel {
  constructor(source: Execution<*>, stageId: String, user: String?) :
    this(source.javaClass, source.getId(), source.getApplication(), stageId, user)
}

data class ResumeStage(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String
) : Message(), StageLevel {
  constructor(source: ExecutionLevel, stageId: String) :
    this(source.executionType, source.executionId, source.application, stageId)
}

data class CancelStage(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String
) : Message(), StageLevel {
  constructor(source: StageLevel) :
    this(source.executionType, source.executionId, source.application, source.stageId)

  constructor(stage: Stage<*>) :
    this(stage.getExecution().javaClass, stage.getExecution().getId(), stage.getExecution().getApplication(), stage.getId())
}

data class StartExecution(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String
) : Message(), ExecutionLevel {
  constructor(source: Execution<*>) :
    this(source.javaClass, source.getId(), source.getApplication())
}

data class CompleteExecution(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String
) : Message(), ExecutionLevel {
  constructor(source: ExecutionLevel) :
    this(source.executionType, source.executionId, source.application)

  constructor(source: Execution<*>) :
    this(source.javaClass, source.getId(), source.getApplication())
}

data class ResumeExecution(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String
) : Message(), ExecutionLevel {
  constructor(source: Execution<*>) :
    this(source.javaClass, source.getId(), source.getApplication())
}

/**
 * Fatal errors in processing the execution configuration.
 */
sealed class ConfigurationError : Message(), ExecutionLevel

/**
 * Execution id was not found in the [ExecutionRepository].
 */
data class InvalidExecutionId(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String
) : ConfigurationError() {
  constructor(source: ExecutionLevel) :
    this(source.executionType, source.executionId, source.application)
}

/**
 * Stage id was not found in the execution.
 */
data class InvalidStageId(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String
) : ConfigurationError(), StageLevel {
  constructor(source: StageLevel) :
    this(source.executionType, source.executionId, source.application, source.stageId)
}

/**
 * Task id was not found in the stage.
 */
data class InvalidTaskId(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String,
  override val taskId: String
) : ConfigurationError(), TaskLevel {
  constructor(source: TaskLevel) :
    this(source.executionType, source.executionId, source.application, source.stageId, source.taskId)
}

/**
 * No such [Task] class.
 */
data class InvalidTaskType(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String,
  val className: String
) : ConfigurationError(), StageLevel {
  constructor(source: StageLevel, className: String) :
    this(source.executionType, source.executionId, source.application, source.stageId, className)
}

data class NoDownstreamTasks(
  override val executionType: Class<out Execution<*>>,
  override val executionId: String,
  override val application: String,
  override val stageId: String,
  override val taskId: String
) : ConfigurationError(), TaskLevel {
  constructor(source: TaskLevel) :
    this(source.executionType, source.executionId, source.application, source.stageId, source.taskId)
}
