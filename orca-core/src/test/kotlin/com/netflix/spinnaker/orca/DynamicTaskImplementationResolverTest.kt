package com.netflix.spinnaker.orca

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.config.TaskOverrideConfigurationProperties
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.nhaarman.mockito_kotlin.doReturn
import org.jetbrains.spek.subject.SubjectSpek
import org.assertj.core.api.Assertions.assertThat
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode

object DynamicTaskImplementationResolverTest: SubjectSpek<DynamicTaskImplementationResolver>({

  val dynamicConfigService: DynamicConfigService = mock()
  val stage: StageExecution = StageExecutionImpl(
    PipelineExecutionImpl(ExecutionType.PIPELINE, "p1", "app"),
    "s1",
    "Test Stage",
    mapOf("cloudProvider" to "aws")
  )

  subject(CachingMode.GROUP) {
    DynamicTaskImplementationResolver(
      dynamicConfigService,
      taskOverrideConfigurationProperties =
      TaskOverrideConfigurationProperties(overrideDefinitions = listOf(
        TaskOverrideConfigurationProperties.TaskOverrideDefinition(
          stageName = "s1",
          overrideCriteriaAttributes = listOf("application", "cloudprovider"),
          originalTaskImplementingClassName = "com.netflix.spinnaker.orca.Task1",
          newTaskImplementingClassName = "com.netflix.spinnaker.orca.Task2"),
        TaskOverrideConfigurationProperties.TaskOverrideDefinition(
          stageName = "s1",
          overrideCriteriaAttributes = listOf("application", "cloudprovider"),
          originalTaskImplementingClassName = "com.netflix.spinnaker.orca.Task3",
          newTaskImplementingClassName = "com.netflix.spinnaker.orca.NotATask")
      )
      )
    )
  }

  fun resetMocks() = reset(dynamicConfigService)

  describe("task def replacement") {
    context("when the task is replaceable") {

      val taskNode: TaskNode.DefinedTask = TaskNode.TaskDefinition("t1", Task1::class.java)

      beforeGroup {
        whenever(dynamicConfigService.isEnabled("task-override.app.aws.s1", false)) doReturn true
      }

      afterGroup { resetMocks() }

      var resultNode: TaskNode.DefinedTask? = null
      on("resolving the new node for the provided stage and tasknode") {
        resultNode = subject.resolve(stage, taskNode)
      }

      it("returns task node t2 as result impl but name remains same") {
        assertThat(resultNode?.implementingClassName?.toLowerCase()).isEqualTo("com.netflix.spinnaker.orca.task2")
        assertThat(resultNode?.name?.toLowerCase()).isEqualTo("t1")
      }

    }

    context("when the task is not replaceable") {

      val taskNode: TaskNode.DefinedTask = TaskNode.TaskDefinition("t2", Task2::class.java)

      beforeGroup {
        whenever(dynamicConfigService.isEnabled("task-override.app.aws.s1", false)) doReturn true
      }

      afterGroup { resetMocks() }
      var resultNode: TaskNode.DefinedTask? = null
      on("resolving the new node for the provided stage and tasknode") {
        resultNode = subject.resolve(stage, taskNode)
      }

      it("returns task node t2 as result with no replacement") {
        assertThat(resultNode?.implementingClassName?.toLowerCase()).isEqualTo("com.netflix.spinnaker.orca.task2")
        assertThat(resultNode?.name?.toLowerCase()).isEqualTo("t2")
      }

    }

    context("task is not replaceable when task impl is not of type Task") {

      val taskNode: TaskNode.DefinedTask = TaskNode.TaskDefinition("t3", Task3::class.java)

      beforeGroup {
        whenever(dynamicConfigService.isEnabled("task-override.app.aws.s1", false)) doReturn true
      }

      afterGroup { resetMocks() }
      var resultNode: TaskNode.DefinedTask? = null
      on("resolving the new node for the provided stage and tasknode") {
        resultNode = subject.resolve(stage, taskNode)
      }

      it("returns task node t3 as result with no replacement") {
        assertThat(resultNode?.implementingClassName?.toLowerCase()).isEqualTo("com.netflix.spinnaker.orca.task3")
        assertThat(resultNode?.name?.toLowerCase()).isEqualTo("t3")
      }

    }
  }

})

class Task1: Task {
  override fun execute(stage: StageExecution): TaskResult {
    return  TaskResult.SUCCEEDED
  }
}

class Task3: Task {
  override fun execute(stage: StageExecution): TaskResult {
    return  TaskResult.SUCCEEDED
  }
}

class Task2: RetryableTask {
  override fun execute(stage: StageExecution): TaskResult {
    return  TaskResult.SUCCEEDED
  }

  override fun getBackoffPeriod(): Long {
    return 0L
  }

  override fun getTimeout(): Long {
    return 0L
  }
}

class NotATask {}

