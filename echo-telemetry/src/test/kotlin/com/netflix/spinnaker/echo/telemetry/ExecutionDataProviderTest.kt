/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.echo.telemetry

import com.netflix.spinnaker.echo.api.events.Event as EchoEvent
import com.netflix.spinnaker.echo.api.events.Metadata
import com.netflix.spinnaker.kork.proto.stats.CloudProvider
import com.netflix.spinnaker.kork.proto.stats.Event as StatsEvent
import com.netflix.spinnaker.kork.proto.stats.Execution
import com.netflix.spinnaker.kork.proto.stats.Status
import org.junit.Assume.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotEqualTo

class ExecutionDataProviderTest {

  @Test
  fun `no execution data`() {

    val echoEvent = createLoggableEvent()
    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.id).isEmpty()
    expectThat(statsEvent.execution.type).isEqualTo(Execution.Type.UNKNOWN)
    expectThat(statsEvent.execution.status).isEqualTo(Status.UNKNOWN)
    expectThat(statsEvent.execution.trigger.type).isEqualTo(Execution.Trigger.Type.UNKNOWN)
    expectThat(statsEvent.execution.stagesList).isEmpty()
  }

  @Test
  fun `execution id is hashed`() {

    val executionData = mapOf(
      "id" to "myExecutionId"
    )
    val echoEvent = createEventWithExecutionData(executionData)

    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.id).isNotEmpty()
    expectThat(statsEvent.execution.id).isNotEqualTo("myExecutionId")
  }

  @Test
  fun `pipeline execution type`() {

    val executionData = mapOf(
      "type" to "pipeline"
    )
    val echoEvent = createEventWithExecutionData(executionData)

    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.type).isEqualTo(Execution.Type.PIPELINE)
  }

  @Test
  fun `orchestration execution type`() {

    val executionData = mapOf(
      "type" to "orchestration"
    )
    val echoEvent = createEventWithExecutionData(executionData)

    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.type).isEqualTo(Execution.Type.ORCHESTRATION)
  }

  @Test
  fun `templatedPipeline execution type with no version`() {

    val executionData = mapOf(
      "type" to "templatedPipeline"
    )
    val echoEvent = createEventWithExecutionData(executionData)

    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.type).isEqualTo(Execution.Type.UNKNOWN)
  }

  @Test
  fun `templatedPipeline v1 execution type`() {

    val executionData = mapOf(
      "type" to "orchestration", // this should be ignored
      "source" to mapOf(
        "type" to "templatedPipeline",
        "version" to "v1"
      )
    )
    val echoEvent = createEventWithExecutionData(executionData)

    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.type).isEqualTo(Execution.Type.MANAGED_PIPELINE_TEMPLATE_V1)
  }

  @Test
  fun `templatedPipeline v2 execution type`() {

    val executionData = mapOf(
      "type" to "orchestration", // this should be ignored
      "source" to mapOf(
        "type" to "templatedPipeline",
        "version" to "v2"
      )
    )
    val echoEvent = createEventWithExecutionData(executionData)

    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.type).isEqualTo(Execution.Type.MANAGED_PIPELINE_TEMPLATE_V2)
  }

  @ParameterizedTest
  @EnumSource
  fun `execution statuses`(status: Status) {

    assumeTrue(status != Status.UNRECOGNIZED)

    val executionData = mapOf(
      "status" to status.toString()
    )
    val echoEvent = createEventWithExecutionData(executionData)
    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.status).isEqualTo(status)
  }

  @ParameterizedTest
  @EnumSource
  fun `trigger types`(triggerType: Execution.Trigger.Type) {

    assumeTrue(triggerType != Execution.Trigger.Type.UNRECOGNIZED)

    val executionData = mapOf(
      "trigger" to mapOf(
        "type" to triggerType.toString()
      )
    )
    val echoEvent = createEventWithExecutionData(executionData)
    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.trigger.type).isEqualTo(triggerType)
  }

  @Test
  fun `basic stage`() {

    val executionData = mapOf(
      "stages" to listOf(
        mapOf(
          "status" to "buffered",
          "type" to "myStageType",
          "context" to mapOf(
            "cloudProvider" to "gce"
          )
        )
      )
    )

    val echoEvent = createEventWithExecutionData(executionData)
    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.stagesList).hasSize(1)
    val stage = statsEvent.execution.stagesList[0]
    expectThat(stage.status).isEqualTo(Status.BUFFERED)
    expectThat(stage.type).isEqualTo("myStageType")
    expectThat(stage.cloudProvider).isEqualTo(
      CloudProvider.newBuilder().setId(CloudProvider.ID.GCE).build())
  }

  @Test
  fun `multiple stages`() {

    val executionData = mapOf(
      "stages" to listOf(
        mapOf(
          "status" to "buffered",
          "type" to "myStageType1",
          "context" to mapOf(
            "cloudProvider" to "gce"
          )
        ),
        mapOf(
          "status" to "redirect",
          "type" to "myStageType2",
          "context" to mapOf(
            "cloudProvider" to "aws"
          )
        ),
        mapOf(
          "status" to "paused",
          "type" to "myStageType3",
          "context" to mapOf(
            "cloudProvider" to "AppEngine"
          )
        )
      )
    )

    val echoEvent = createEventWithExecutionData(executionData)
    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.stagesList).hasSize(3)

    val stage1 = statsEvent.execution.stagesList[0]
    expectThat(stage1.status).isEqualTo(Status.BUFFERED)
    expectThat(stage1.type).isEqualTo("myStageType1")
    expectThat(stage1.cloudProvider).isEqualTo(
      CloudProvider.newBuilder().setId(CloudProvider.ID.GCE).build())

    val stage2 = statsEvent.execution.stagesList[1]
    expectThat(stage2.status).isEqualTo(Status.REDIRECT)
    expectThat(stage2.type).isEqualTo("myStageType2")
    expectThat(stage2.cloudProvider).isEqualTo(
      CloudProvider.newBuilder().setId(CloudProvider.ID.AWS).build())

    val stage3 = statsEvent.execution.stagesList[2]
    expectThat(stage3.status).isEqualTo(Status.PAUSED)
    expectThat(stage3.type).isEqualTo("myStageType3")
    expectThat(stage3.cloudProvider).isEqualTo(
      CloudProvider.newBuilder().setId(CloudProvider.ID.APPENGINE).build())
  }

  @Test
  fun `split stages with multiple cloud providers`() {

    val executionData = mapOf(
      "stages" to listOf(
        mapOf(
          "status" to "buffered",
          "type" to "myStageType",
          "context" to mapOf(
            "newState" to mapOf(
              "cloudProviders" to "gce,aws,appengine"
            )
          )
        )
      )
    )

    val echoEvent = createEventWithExecutionData(executionData)
    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.stagesList).hasSize(3)

    val stage1 = statsEvent.execution.stagesList[0]
    expectThat(stage1.status).isEqualTo(Status.BUFFERED)
    expectThat(stage1.type).isEqualTo("myStageType")
    expectThat(stage1.cloudProvider).isEqualTo(
      CloudProvider.newBuilder().setId(CloudProvider.ID.GCE).build())

    val stage2 = statsEvent.execution.stagesList[1]
    expectThat(stage2.status).isEqualTo(Status.BUFFERED)
    expectThat(stage2.type).isEqualTo("myStageType")
    expectThat(stage2.cloudProvider).isEqualTo(
      CloudProvider.newBuilder().setId(CloudProvider.ID.AWS).build())

    val stage3 = statsEvent.execution.stagesList[2]
    expectThat(stage3.status).isEqualTo(Status.BUFFERED)
    expectThat(stage3.type).isEqualTo("myStageType")
    expectThat(stage3.cloudProvider).isEqualTo(
      CloudProvider.newBuilder().setId(CloudProvider.ID.APPENGINE).build())
  }

  @Test
  fun `stage with no cloud provider`() {

    val executionData = mapOf(
      "stages" to listOf(
        mapOf(
          "status" to "buffered",
          "type" to "myStageType"
        )
      )
    )

    val echoEvent = createEventWithExecutionData(executionData)
    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.stagesList).hasSize(1)
    val stage = statsEvent.execution.stagesList[0]
    expectThat(stage.cloudProvider).isEqualTo(CloudProvider.getDefaultInstance())
  }

  @ParameterizedTest
  @EnumSource
  fun `all stage status values`(status: Status) {

    assumeTrue(status != Status.UNRECOGNIZED)

    val executionData = mapOf(
      "stages" to listOf(
        mapOf("status" to status.toString())
      )
    )

    val echoEvent = createEventWithExecutionData(executionData)
    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.stagesList).hasSize(1)
    val stage = statsEvent.execution.stagesList[0]
    expectThat(stage.status).isEqualTo(status)
  }

  @ParameterizedTest
  @EnumSource
  fun `all cloud providers`(cloudProviderId: CloudProvider.ID) {

    assumeTrue(cloudProviderId != CloudProvider.ID.UNRECOGNIZED)

    val executionData = mapOf(
      "stages" to listOf(
        mapOf(
          "context" to mapOf("cloudProvider" to cloudProviderId.toString()))
      )
    )

    val echoEvent = createEventWithExecutionData(executionData)
    val statsEvent = ExecutionDataProvider().populateData(
      echoEvent, StatsEvent.getDefaultInstance())

    expectThat(statsEvent.execution.stagesList).hasSize(1)
    val stage = statsEvent.execution.stagesList[0]
    expectThat(stage.cloudProvider)
      .isEqualTo(CloudProvider.newBuilder().setId(cloudProviderId).build())
      .isEqualTo(CloudProvider.newBuilder().setId(cloudProviderId).build())
  }

  private fun createLoggableEvent(): EchoEvent {
    return EchoEvent().apply {
      details = Metadata().apply {
        type = "orca:orchestration:complete"
        application = "application"
      }
      content = mapOf()
    }
  }

  private fun createEventWithExecutionData(execution: Map<String, Any>): EchoEvent {
    return createLoggableEvent().apply {
      content = mapOf("execution" to execution)
    }
  }
}
