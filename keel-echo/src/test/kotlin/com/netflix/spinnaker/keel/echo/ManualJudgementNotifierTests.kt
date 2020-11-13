package com.netflix.spinnaker.keel.echo

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.NotificationType
import com.netflix.spinnaker.keel.api.artifacts.BaseLabel.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.events.ConstraintStateChanged
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.echo.model.EchoNotification
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isGreaterThan
import strikt.assertions.isNull

internal class ManualJudgementNotifierTests : JUnit5Minutests {

  companion object {
    val manualJudgementConstraint = ManualJudgementConstraint()
  }

  data class Fixture(
    val event: ConstraintStateChanged
  ) {
    val keelNotificationConfig: com.netflix.spinnaker.config.KeelNotificationConfig = mockk(relaxed = true)
    val echoService: EchoService = mockk(relaxed = true)
    val repository: KeelRepository = mockk(relaxed = true)
    val baseUrl = "https://spinnaker.acme.net"
    val artifact = DebianArtifact("mypkg", "test", "deb",
      vmOptions = VirtualMachineOptions(RELEASE, "bionic", emptySet())
    )
    val subject = ManualJudgementNotifier(keelNotificationConfig, echoService, repository, baseUrl)
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(
        ConstraintStateChanged(
          environment = Environment(
            name = "test",
            notifications = setOf(
              NotificationConfig(
                type = NotificationType.slack,
                address = "#test",
                frequency = NotificationFrequency.normal
              )
            ),
            resources = setOf(
              resource()
            )
          ),
          constraint = manualJudgementConstraint,
          previousState = null,
          currentState = ConstraintState(
            uid = randomUID(),
            deliveryConfigName = "test",
            environmentName = "test",
            artifactVersion = "v1.0.0",
            artifactReference = "deb",
            type = manualJudgementConstraint.type,
            status = ConstraintStatus.PENDING
          )
        )
      )
    }

    context("manual judgement constraint state changed event received") {
      before {
        coEvery {
          echoService.sendNotification(any())
        } just Runs

        every {
          keelNotificationConfig.enabled
        } returns true

        every {
          repository.getArtifact("test", "deb")
        } returns DebianArtifact("mypkg", "test", "deb",
            vmOptions = VirtualMachineOptions(RELEASE, "bionic", emptySet()))

        every {
          repository.getDeliveryConfig("test")
        } returns DeliveryConfig("test", "test", "test@acme.net")

        every {
          repository.getArtifactVersion(artifact, "v1.0.0", any())
        } returns null
      }

      test("throws an exception if the constraint state uid is not present") {
        expectCatching {
          subject.constraintStateChanged(event.withoutStateUid())
        }.isFailure()

        coVerify(exactly = 0) {
          echoService.sendNotification(any())
        }
      }

      test("generates a notification if it's the first transition to PENDING") {
        subject.constraintStateChanged(event)

        coVerify(exactly = 1) {
          echoService.sendNotification(any())
        }
      }

      test("does NOT generate a notification if it's not the first state transition") {
        subject.constraintStateChanged(event.withPreviousState())

        coVerify(exactly = 0) {
          echoService.sendNotification(any())
        }
      }

      test("generates one notification for each notification in the environment config") {
        val event = event.withMultipleNotificationConfigs()
        subject.constraintStateChanged(event)

        expectThat(event.environment.notifications.size).isGreaterThan(1)

        coVerify(exactly = event.environment.notifications.size) {
          echoService.sendNotification(any())
        }
      }

      test("the notification includes interactive actions for the user to approve/reject manual judgement") {
        val expectedInteractiveActions = with(event) {
          EchoNotification.InteractiveActions(
            callbackServiceId = "keel",
            callbackMessageId = currentState.uid!!.toString(),
            actions = listOf(
              EchoNotification.ButtonAction(
                name = "manual-judgement",
                label = "Approve",
                value = ConstraintStatus.OVERRIDE_PASS.name
              ),
              EchoNotification.ButtonAction(
                name = "manual-judgement",
                label = "Reject",
                value = ConstraintStatus.OVERRIDE_FAIL.name
              )
            ),
            color = "#fcba03"
          )
        }

        subject.constraintStateChanged(event)

        val notification = slot<EchoNotification>()

        coVerify {
          echoService.sendNotification(capture(notification))
        }

        expectThat(notification.captured.interactiveActions).isEqualTo(expectedInteractiveActions)
      }

      test("the notification includes details about the artifact and environment") {
        subject.constraintStateChanged(event)

        val notification = slot<EchoNotification>()

        coVerify {
          echoService.sendNotification(capture(notification))
        }

        val notificationBody = notification.captured.additionalContext!!["body"].toString()
        expectThat(notificationBody)
          .contains(":warning: Manual approval required to deploy artifact *mypkg*")
        expectThat(notificationBody)
          .contains("*Version:* <$baseUrl/#/applications/test/environments/deb/v1.0.0|v1.0.0>")
        expectThat(notificationBody)
          .contains("*Application:* test")
        expectThat(notificationBody)
          .contains("*Environment:* test")
      }

      context("when git metadata is available for the artifact") {
        before {
          every {
            repository.getArtifactVersion(artifact, "v1.0.0", any())
          } returns PublishedArtifact(
            name = "mypkg",
            type = DEBIAN,
            version = "v1.0.0",
            gitMetadata = GitMetadata(
              commit = "a1b2c3d",
              author = "joesmith",
              project = "myproj",
              branch = "master",
              repo = Repo("myapp"),
              commitInfo = Commit(message = "A test commit")
            )
          )
        }

        test("the notification includes git metadata") {
          subject.constraintStateChanged(event)

          val notification = slot<EchoNotification>()

          coVerify {
            echoService.sendNotification(capture(notification))
          }

          val notificationBody = notification.captured.additionalContext!!["body"].toString()
          expectThat(notificationBody)
            .contains("*Commit:* a1b2c3d")
          expectThat(notificationBody)
            .contains("*Author:* joesmith")
          expectThat(notificationBody)
            .contains("*Repo:* myproj/myapp")
          expectThat(notificationBody)
            .contains("*Branch:* master")
          expectThat(notificationBody)
            .contains("*Message:* A test commit")
        }
      }
    }

    context("with interactive notifications disabled") {
      before {
        every {
          keelNotificationConfig.enabled
        } returns false
      }

      test("no interactive actions are included in the notification") {
        subject.constraintStateChanged(event)
        val notification = slot<EchoNotification>()

        coVerify {
          echoService.sendNotification(capture(notification))
        }

        expectThat(notification.captured.interactiveActions).isNull()
        expectThat((notification.captured.additionalContext?.get("body") as String).contains(ManualJudgementNotifier.MANUAL_JUDGEMENT_DOC_URL))
      }
    }
  }

  private fun ConstraintStateChanged.withoutStateUid() =
    copy(currentState = currentState.copy(uid = null))

  private fun ConstraintStateChanged.withPreviousState() =
    copy(previousState = mockk())

  private fun ConstraintStateChanged.withMultipleNotificationConfigs() =
    copy(
      environment = environment.copy(
        notifications = environment.notifications +
          NotificationConfig(
            type = NotificationType.email,
            address = "john@doe.com",
            frequency = NotificationFrequency.normal
          )
      )
    )
}
