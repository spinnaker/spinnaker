package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Instant

internal class OrcaLinkStrategyTests {

  @Test
  fun `generates a task url from an ExecutionDetailResponse`() {
    val strategy = OrcaLinkStrategy("https://spin")
    val response = makeResponse("fnord", "01FANMT9Z7376DN9X3XVK966AK")

    expectThat(strategy.url(response))
      .isEqualTo("https://spin/#/applications/fnord/tasks/01FANMT9Z7376DN9X3XVK966AK")
  }

  private fun makeResponse(application: String, id: String) =
    ExecutionDetailResponse(
      id,
      "name",
      application,
      Instant.now(),
      null,
      null,
      TaskStatus.NOT_STARTED
    )
}
