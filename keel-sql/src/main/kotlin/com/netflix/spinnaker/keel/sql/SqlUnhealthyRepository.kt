package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.UnhealthyRepository
import com.netflix.spinnaker.keel.persistence.metamodel.tables.Unhealthy.UNHEALTHY
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import org.jooq.DSLContext
import java.time.Clock
import java.time.Duration

class SqlUnhealthyRepository(
  val clock: Clock,
  val jooq: DSLContext,
  val sqlRetry: SqlRetry
) : UnhealthyRepository() {
  override fun markUnhealthy(resourceId: String) {
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(UNHEALTHY)
        .set(UNHEALTHY.RESOURCE_ID, resourceId)
        .set(UNHEALTHY.TIME_DETECTED, clock.timestamp())
        .onDuplicateKeyIgnore()
        .execute()
    }
  }

  override fun markHealthy(resourceId: String) {
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(UNHEALTHY)
        .where(UNHEALTHY.RESOURCE_ID.eq(resourceId))
        .execute()
    }
  }

  override fun durationUnhealthy(resourceId: String): Duration {
    val detectedTime = sqlRetry.withRetry(READ) {
      jooq.select(UNHEALTHY.TIME_DETECTED)
        .from(UNHEALTHY)
        .where(UNHEALTHY.RESOURCE_ID.eq(resourceId))
        .fetchOne(UNHEALTHY.TIME_DETECTED)
    } ?: return Duration.ZERO

    return Duration.between(detectedTime, clock.timestamp())
  }
}
