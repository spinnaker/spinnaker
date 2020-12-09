package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.persistence.UnhealthyRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.tables.Unhealthy.UNHEALTHY
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import java.time.Clock
import java.time.Duration

class SqlUnhealthyRepository(
  val clock: Clock,
  val jooq: DSLContext,
  val sqlRetry: SqlRetry
) : UnhealthyRepository() {
  override fun markUnhealthy(resource: Resource<*>) {
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(UNHEALTHY)
        .set(UNHEALTHY.RESOURCE_UID, resource.uid)
        .set(UNHEALTHY.TIME_DETECTED, clock.instant())
        .onDuplicateKeyIgnore()
        .execute()
    }
  }

  override fun markHealthy(resource: Resource<*>) {
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(UNHEALTHY)
        .where(UNHEALTHY.RESOURCE_UID.eq(resource.uid))
        .execute()
    }
  }

  override fun durationUnhealthy(resource: Resource<*>): Duration {
    val detectedTime = sqlRetry.withRetry(READ) {
      jooq.select(UNHEALTHY.TIME_DETECTED)
        .from(UNHEALTHY)
        .where(UNHEALTHY.RESOURCE_UID.eq(resource.uid))
        .fetchOne(UNHEALTHY.TIME_DETECTED)
    } ?: return Duration.ZERO

    return Duration.between(detectedTime, clock.instant())
  }

  private val Resource<*>.uid: Select<Record1<String>>
    get() = jooq.select(RESOURCE.UID).from(RESOURCE).where(RESOURCE.ID.eq(id))
}
