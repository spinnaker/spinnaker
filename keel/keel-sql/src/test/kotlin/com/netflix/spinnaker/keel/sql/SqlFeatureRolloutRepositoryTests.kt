package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.rollout.RolloutStatus
import com.netflix.spinnaker.keel.rollout.RolloutStatus.IN_PROGRESS
import com.netflix.spinnaker.keel.rollout.RolloutStatus.NOT_STARTED
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Clock

internal class SqlFeatureRolloutRepositoryTests {
  private val jooq = testDatabase.context
  private val sqlRetry = RetryProperties(1, 0).let { SqlRetry(SqlRetryProperties(it, it)) }
  private val subject = SqlFeatureRolloutRepository(jooq, sqlRetry, Clock.systemDefaultZone())

  private val feature = "commencement-of-eschaton"
  private val resourceId = "titus:cluster:prod:fnord-main"

  @AfterEach
  fun flush() {
    cleanupDb(jooq)
  }

  @Test
  fun `if a feature has never been rolled out to a resource the status is NOT_STARTED and the count is zero`() {
    subject.rolloutStatus(feature, resourceId)
      .also { result ->
        expectThat(result) isEqualTo (NOT_STARTED to 0)
      }
  }

  @Test
  fun `if a feature has been rolled out once to a resource the status is IN_PROGRESS and the count is one`() {
    with(subject) {
      markRolloutStarted(feature, resourceId)
      rolloutStatus(feature, resourceId)
        .also { result ->
          expectThat(result) isEqualTo (IN_PROGRESS to 1)
        }
    }
  }

  @ParameterizedTest(name = "if a feature rollout completed with status {arguments}, its status reflects that")
  @EnumSource(RolloutStatus::class, names = ["SKIPPED", "FAILED", "SUCCESSFUL"])
  fun `if a feature rollout completed its status reflects that`(status: RolloutStatus) {
    with(subject) {
      markRolloutStarted(feature, resourceId)
      updateStatus(feature, resourceId, status)
      rolloutStatus(feature, resourceId)
        .also { result ->
          expectThat(result) isEqualTo (status to 1)
        }
    }
  }

  @Test
  fun `multiple rollout attempts increment the count`() {
    val n = 5
    with(subject) {
      repeat(n) {
        markRolloutStarted(feature, resourceId)
      }
      rolloutStatus(feature, resourceId)
        .also { result ->
          expectThat(result) isEqualTo (IN_PROGRESS to n)
        }
    }
  }

  @Test
  fun `does not mix the counts for different features`() {
    with(subject) {
      markRolloutStarted(feature, resourceId)
      rolloutStatus("a-different-feature", resourceId)
        .also { result ->
          expectThat(result) isEqualTo (NOT_STARTED to 0)
        }
    }
  }

  @Test
  fun `does not mix the counts for different resources`() {
    with(subject) {
      markRolloutStarted(feature, resourceId)
      rolloutStatus(feature, "titus:cluster:test:fnord-test")
        .also { result ->
          expectThat(result) isEqualTo (NOT_STARTED to 0)
        }
    }
  }
}
