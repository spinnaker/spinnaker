package com.netflix.spinnaker.keel.notifications.slack.callbacks

import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.auth.AuthorizationResourceType.APPLICATION
import com.netflix.spinnaker.keel.auth.AuthorizationResourceType.SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.auth.PermissionLevel.WRITE
import com.netflix.spinnaker.keel.core.api.parseUID
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.time.MutableClock
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload
import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import com.slack.api.model.Message
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.core.env.Environment
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue
import java.time.Instant
import java.time.ZoneId

class ManualJudgmentCallbackHandlerTests : JUnit5Minutests {

  class Fixture {
    val repository: KeelRepository = mockk() {
      every {getDeliveryConfig(any())} returns deliveryConfig()
    }
    val slackService: SlackService = mockk()
    val authorizationSupport: AuthorizationSupport = mockk() {
      every { hasPermission(any(), any(), any(), any()) } returns true
    }

    val springEnv: Environment = mockk() {
      every {
        getProperty("slack.authorize-manual-judgement", Boolean::class.java, false)
      } returns true
    }

    val clock: MutableClock = MutableClock(
      Instant.parse("2020-03-25T00:00:00.00Z"),
      ZoneId.of("UTC")
    )

    val constraintUid = "01EQW0XKNR5H2NMPJXP020EQXE"

    fun buildPayload(actionPerformed: String): BlockActionPayload {
      val user = BlockActionPayload.User()
      user.id = "01234"
      user.name = "keel-user"
      user.username = "keel"
      val action = BlockActionPayload.Action()
      action.actionId = "$constraintUid:$actionPerformed:MANUAL_JUDGMENT"
      action.value = actionPerformed
      val message = Message()
      return BlockActionPayload.builder()
        .user(user)
        .actions(listOf(action))
        .message(message)
        .build()
    }


    val pendingManualJudgement = ConstraintState(
      "myconfig",
      "testing",
      "1.0.0",
      "my-debian",
      "manual-judgement",
      ConstraintStatus.PENDING
    )

    val subject = ManualJudgmentCallbackHandler(clock, repository, slackService, authorizationSupport, springEnv)
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("handling manual judgment response") {
      before {
        every {
          repository.getConstraintStateById(parseUID(constraintUid))
        } returns pendingManualJudgement

        every {
          slackService.getEmailByUserId(any())
        } returns "keel@keel"

        every {
          repository.storeConstraintState(any())
        } just Runs

      }

      test("update status correctly with approval") {
        val slot = slot<ConstraintState>()
        subject.updateConstraintState(buildPayload("OVERRIDE_PASS"), pendingManualJudgement)
        verify(exactly = 1) {
          repository.storeConstraintState(capture(slot))
        }
        expectThat(slot.captured.status).isEqualTo(ConstraintStatus.OVERRIDE_PASS)
        expectThat(slot.captured.judgedBy).isEqualTo("keel@keel")
      }

      test("update status correctly with rejection") {
        val slot = slot<ConstraintState>()
        subject.updateConstraintState(buildPayload("OVERRIDE_FAIL"), pendingManualJudgement)
        verify(exactly = 1) {
          repository.storeConstraintState(capture(slot))
        }
        expectThat(slot.captured.status).isEqualTo(ConstraintStatus.OVERRIDE_FAIL)
        expectThat(slot.captured.judgedBy).isEqualTo("keel@keel")
      }
    }

    context("checking authorization") {
      context("authz check disabled") {
        before {
          every {
            springEnv.getProperty("slack.authorize-manual-judgement", Boolean::class.java, false)
          } returns false
        }

        test("authz passes") {
          val req: BlockActionRequest = mockk() {
            every { payload } returns buildPayload("OVERRIDE_PASS")
          }
          val ctx: ActionContext = mockk() {
            every { requestUserId } returns "01234"
          }
          val response = subject.validateAuth(req, ctx, pendingManualJudgement)
          expectThat(response.authorized).isTrue()
          expectThat(response.errorMessage).isNull()
          verify(exactly = 0) { authorizationSupport.hasPermission(any(), any(), any(), any()) }
        }

      }

      context("no email found") {
        before {
          every { slackService.getEmailByUserId("01234") } returns "01234"
        }

        test("unauthorized if no email found") {
          val req: BlockActionRequest = mockk() {
            every { payload } returns buildPayload("OVERRIDE_PASS")
          }
          val ctx: ActionContext = mockk() {
            every { requestUserId } returns "01234"
          }
          val response = subject.validateAuth(req, ctx, pendingManualJudgement)
          expectThat(response.authorized).isFalse()
          expectThat(response.errorMessage?.contains("email address")).isTrue()
        }
      }

      context("checking app and service account permissions") {
        before {
          every { slackService.getEmailByUserId("01234") } returns "pancake@breakfast.io"
        }

        test("unauthorized if no service account permissions") {
          every { authorizationSupport.hasPermission("pancake@breakfast.io", any(), APPLICATION, WRITE) } returns true
          every {
            authorizationSupport.hasPermission(
              "pancake@breakfast.io",
              any(),
              SERVICE_ACCOUNT,
              WRITE
            )
          } returns false

          val req: BlockActionRequest = mockk() {
            every { payload } returns buildPayload("OVERRIDE_PASS")
          }
          val ctx: ActionContext = mockk() {
            every { requestUserId } returns "01234"
          }
          val response = subject.validateAuth(req, ctx, pendingManualJudgement)
          expectThat(response.authorized).isFalse()
          expectThat(response.errorMessage?.contains("deploy this app")).isFalse()
          expectThat(response.errorMessage?.contains("service account")).isTrue()
        }

        test("unauthorized if no app permissions") {
          every { authorizationSupport.hasPermission("pancake@breakfast.io", any(), APPLICATION, WRITE) } returns false
          every {
            authorizationSupport.hasPermission(
              "pancake@breakfast.io",
              any(),
              SERVICE_ACCOUNT,
              WRITE
            )
          } returns true
          val req: BlockActionRequest = mockk() {
            every { payload } returns buildPayload("OVERRIDE_PASS")
          }
          val ctx: ActionContext = mockk() {
            every { requestUserId } returns "01234"
          }
          val response = subject.validateAuth(req, ctx, pendingManualJudgement)
          expectThat(response.authorized).isFalse()
          expectThat(response.errorMessage?.contains("service account")).isFalse()
          expectThat(response.errorMessage?.contains("deploy this app")).isTrue()
        }

        test("unauthorized if no app and service account permissions") {
          every { authorizationSupport.hasPermission("pancake@breakfast.io", any(), APPLICATION, WRITE) } returns false
          every {
            authorizationSupport.hasPermission(
              "pancake@breakfast.io",
              any(),
              SERVICE_ACCOUNT,
              WRITE
            )
          } returns false
          val req: BlockActionRequest = mockk() {
            every { payload } returns buildPayload("OVERRIDE_PASS")
          }
          val ctx: ActionContext = mockk() {
            every { requestUserId } returns "01234"
          }
          val response = subject.validateAuth(req, ctx, pendingManualJudgement)
          expectThat(response.authorized).isFalse()
          expectThat(response.errorMessage?.contains("service account")).isTrue()
          expectThat(response.errorMessage?.contains("deploy this app")).isTrue()
        }
      }
      context("fully authorized") {
        before {
          every { slackService.getEmailByUserId("01234") } returns "pancake@breakfast.io"
        }

        test("no error message is returned") {
          every { authorizationSupport.hasPermission("pancake@breakfast.io", any(), APPLICATION, WRITE) } returns true
          every {
            authorizationSupport.hasPermission(
              "pancake@breakfast.io",
              any(),
              SERVICE_ACCOUNT,
              WRITE
            )
          } returns true

          val req: BlockActionRequest = mockk() {
            every { payload } returns buildPayload("OVERRIDE_PASS")
          }
          val ctx: ActionContext = mockk() {
            every { requestUserId } returns "01234"
          }
          val response = subject.validateAuth(req, ctx, pendingManualJudgement)
          expectThat(response.authorized).isTrue()
          expectThat(response.errorMessage).isNull()
        }
      }
    }
  }
}
