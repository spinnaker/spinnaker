package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.FeatureRolloutRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.FEATURE_ROLLOUT
import com.netflix.spinnaker.keel.rollout.RolloutStatus
import com.netflix.spinnaker.keel.rollout.RolloutStatus.IN_PROGRESS
import com.netflix.spinnaker.keel.rollout.RolloutStatus.NOT_STARTED
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import org.jooq.DSLContext
import java.time.Clock

class SqlFeatureRolloutRepository(
  private val jooq: DSLContext,
  private val sqlRetry: SqlRetry,
  private val clock: Clock
) : FeatureRolloutRepository {
  override fun markRolloutStarted(feature: String, resourceId: String) {
    sqlRetry.withRetry(WRITE) {
      jooq
        .insertInto(FEATURE_ROLLOUT)
        .set(FEATURE_ROLLOUT.FEATURE, feature)
        .set(FEATURE_ROLLOUT.RESOURCE_ID, resourceId)
        .set(FEATURE_ROLLOUT.STATUS, IN_PROGRESS)
        .set(FEATURE_ROLLOUT.ATTEMPTS, 1)
        .set(FEATURE_ROLLOUT.FIRST_ATTEMPT_AT, clock.instant())
        .set(FEATURE_ROLLOUT.LATEST_ATTEMPT_AT, clock.instant())
        .onDuplicateKeyUpdate()
        .set(FEATURE_ROLLOUT.STATUS, IN_PROGRESS)
        .set(FEATURE_ROLLOUT.ATTEMPTS, FEATURE_ROLLOUT.ATTEMPTS + 1)
        .set(FEATURE_ROLLOUT.LATEST_ATTEMPT_AT, clock.instant())
        .execute()
    }
  }

  override fun rolloutStatus(feature: String, resourceId: String): Pair<RolloutStatus, Int> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(FEATURE_ROLLOUT.STATUS, FEATURE_ROLLOUT.ATTEMPTS)
        .from(FEATURE_ROLLOUT)
        .where(FEATURE_ROLLOUT.FEATURE.eq(feature))
        .and(FEATURE_ROLLOUT.RESOURCE_ID.eq(resourceId))
        .fetchOne { (status, attempts) -> status to attempts } ?: (NOT_STARTED to 0)
    }

  override fun updateStatus(feature: String, resourceId: String, status: RolloutStatus) {
    sqlRetry.withRetry(WRITE) {
      jooq
        .insertInto(FEATURE_ROLLOUT)
        .set(FEATURE_ROLLOUT.FEATURE, feature)
        .set(FEATURE_ROLLOUT.RESOURCE_ID, resourceId)
        .set(FEATURE_ROLLOUT.STATUS, status)
        .set(FEATURE_ROLLOUT.ATTEMPTS, 0)
        .setNull(FEATURE_ROLLOUT.FIRST_ATTEMPT_AT)
        .setNull(FEATURE_ROLLOUT.LATEST_ATTEMPT_AT)
        .onDuplicateKeyUpdate()
        .set(FEATURE_ROLLOUT.STATUS, status)
        .execute()
    }
  }
}
