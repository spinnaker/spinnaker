package com.netflix.spinnaker.keel.slack.callbacks

import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.core.api.parseUID
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.slack.SlackService
import com.netflix.spinnaker.time.MutableClock
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload
import com.slack.api.model.Message
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Instant
import java.time.ZoneId

class ManualJudgmentCallbackHandlerTests : JUnit5Minutests {

  class Fixture {
    val repository: KeelRepository = mockk()
    val slackService: SlackService = mockk()

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
      "delivery_config",
      "testing",
      "1.0.0",
      "my-debian",
      "manual-judgement",
      ConstraintStatus.PENDING
    )

    val subject = ManualJudgmentCallbackHandler(clock, repository, slackService)
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
        subject.updateConstraintState(buildPayload("OVERRIDE_PASS"))
        verify(exactly = 1) {
          repository.storeConstraintState(capture(slot))
        }
        expectThat(slot.captured.status).isEqualTo(ConstraintStatus.OVERRIDE_PASS)
        expectThat(slot.captured.judgedBy).isEqualTo("keel@keel")
      }

      test("update status correctly with rejection") {
        val slot = slot<ConstraintState>()
        subject.updateConstraintState(buildPayload("OVERRIDE_FAIL"))
        verify(exactly = 1) {
          repository.storeConstraintState(capture(slot))
        }
        expectThat(slot.captured.status).isEqualTo(ConstraintStatus.OVERRIDE_FAIL)
        expectThat(slot.captured.judgedBy).isEqualTo("keel@keel")
      }
    }
  }
}
