package com.netflix.spinnaker.keel.orchestration

import com.netflix.spinnaker.keel.api.AssetId
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.CANCELED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.PAUSED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.RUNNING
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.SKIPPED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.SUSPENDED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.TERMINAL
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskDetailResponse
import com.netflix.spinnaker.keel.orca.TaskRef
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.time.Instant.now

abstract class TaskMonitorSpec<T : TaskMonitor>(
  private val createSubject: (OrcaService) -> T
) : Spek({

  val orcaService: OrcaService = mock()

  describe("monitoring tasks") {

    val assetId = AssetId
      .newBuilder()
      .setValue("keel:ec2.SecurityGroup:mgmt:us-west-2:keel")
      .build()
    val taskRef = TaskRef("/tasks/1")

    given("no tasks have been registered") {
      val subject = createSubject(orcaService)

      it("does not consider an asset to be in progress") {
        expectThat(subject.isInProgress(assetId)).isFalse()
      }
    }

    given("a task has been registered") {

      sequenceOf(NOT_STARTED, RUNNING, PAUSED, SUSPENDED).forEach { status ->
        given("Orca reports the task as $status") {
          val subject = createSubject(orcaService)

          beforeGroup {
            subject.monitorTask(assetId, taskRef)

            whenever(orcaService.getTask(taskRef.id)) doReturn TaskDetailResponse(
              id = "1",
              name = "upsert security group",
              application = "keel",
              buildTime = now().minusSeconds(30),
              startTime = now().minusSeconds(30),
              endTime = null,
              status = status
            )
          }

          afterGroup { reset(orcaService) }

          it("considers the asset to be in progress") {
            expectThat(subject.isInProgress(assetId)).isTrue()
          }

          it("checks Orca if asked again") {
            subject.isInProgress(assetId)

            verify(orcaService, times(2)).getTask(taskRef.id)
          }
        }
      }

      sequenceOf(SUCCEEDED, TERMINAL, CANCELED, SKIPPED).forEach { status ->
        given("Orca reports the task as $status") {
          val subject = createSubject(orcaService)

          beforeGroup {
            subject.monitorTask(assetId, taskRef)

            whenever(orcaService.getTask(taskRef.id)) doReturn TaskDetailResponse(
              id = "1",
              name = "upsert security group",
              application = "keel",
              buildTime = now().minusSeconds(300),
              startTime = now().minusSeconds(300),
              endTime = null,
              status = status
            )
          }

          afterGroup { reset(orcaService) }

          it("considers the asset to not be in progress") {
            expectThat(subject.isInProgress(assetId)).isFalse()
          }

          it("does not check Orca if asked again") {
            subject.isInProgress(assetId)

            verify(orcaService, times(1)).getTask(taskRef.id)
          }
        }
      }
    }
  }
})
