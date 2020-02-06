package com.netflix.spinnaker.keel.echo

import com.netflix.spinnaker.config.ManualJudgementNotificationConfig
import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.NotificationType
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.echo.model.EchoNotification
import com.netflix.spinnaker.keel.events.ConstraintStateChanged
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
import strikt.assertions.failed
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNull

internal class ManualJudgementNotifierTests : JUnit5Minutests {

  companion object {
    val manualJudgementConstraint = ManualJudgementConstraint()
  }

  data class Fixture(
    val event: ConstraintStateChanged
  ) {
    val notificationConfig: ManualJudgementNotificationConfig = mockk(relaxed = true)
    val echoService: EchoService = mockk(relaxed = true)
    val subject = ManualJudgementNotifier(notificationConfig = notificationConfig, echoService = echoService)
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
          notificationConfig.enabled
        } returns true
      }

      test("throws an exception if the constraint state uid is not present") {
        expectCatching {
          subject.constraintStateChanged(event.withoutStateUid())
        }.failed()

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
        val config = event.environment.notifications.first()

        val expectedNotification = with(event) {
          EchoNotification(
            notificationType = EchoNotification.Type.valueOf(config.type.name.toUpperCase()),
            to = listOf(config.address),
            severity = EchoNotification.Severity.NORMAL,
            source = EchoNotification.Source(
              application = environment.resources.first().application
            ),
            additionalContext = mapOf(
              "formatter" to "MARKDOWN",
              "subject" to "Manual artifact promotion approval",
              "body" to
                ":warning: The artifact *${currentState.artifactVersion}* from delivery config " +
                "*${currentState.deliveryConfigName}* requires your manual approval for deployment " +
                "into the *${currentState.environmentName}* environment."
            ),
            interactiveActions = EchoNotification.InteractiveActions(
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
          )
        }

        subject.constraintStateChanged(event)

        val notification = slot<EchoNotification>()

        coVerify {
          echoService.sendNotification(capture(notification))
        }

        expectThat(notification.captured).isEqualTo(expectedNotification)
      }
    }

    context("with interactive notifications disabled") {
      before {
        every {
          notificationConfig.enabled
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
            frequency = NotificationFrequency.normal)
        )
    )
}
