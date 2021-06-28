package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.persistence.EnvironmentDeletionRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTIVE_ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_DELETION
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import com.netflix.spinnaker.keel.sql.deliveryconfigs.makeEnvironment
import com.netflix.spinnaker.keel.sql.deliveryconfigs.selectEnvironmentColumns
import org.jooq.DSLContext
import java.time.Clock
import java.time.Duration

/**
 * SQL-based implementation of [EnvironmentDeletionRepository].
 */
class SqlEnvironmentDeletionRepository(
  jooq: DSLContext,
  clock: Clock,
  resourceSpecIdentifier: ResourceSpecIdentifier,
  objectMapper: ObjectMapper,
  sqlRetry: SqlRetry,
  artifactSuppliers: List<ArtifactSupplier<*, *>> = emptyList(),
  specMigrators: List<SpecMigrator<*, *>> = emptyList(),
) : SqlStorageContext(
  jooq,
  clock,
  sqlRetry,
  objectMapper,
  resourceSpecIdentifier,
  artifactSuppliers,
  specMigrators
), EnvironmentDeletionRepository {

  override fun markForDeletion(environment: Environment) {
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(ENVIRONMENT_DELETION)
        .set(ENVIRONMENT_DELETION.ENVIRONMENT_UID, environment.uid
          ?: error("Missing UID for environment: $environment")
        )
        .set(ENVIRONMENT_DELETION.LAST_CHECKED_AT, clock.instant())
        .onDuplicateKeyUpdate()
        .set(ENVIRONMENT_DELETION.LAST_CHECKED_AT, clock.instant())
        .execute()
    }
  }

  override fun isMarkedForDeletion(environment: Environment): Boolean {
    return sqlRetry.withRetry(READ) {
      jooq.fetchExists(
        ENVIRONMENT_DELETION,
        ENVIRONMENT_DELETION.ENVIRONMENT_UID.eq(environment.uid)
      )
    }
  }

  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<Environment> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck)
    val environmentUids = sqlRetry.withRetry(WRITE) {
      jooq.inTransaction {
        select(ENVIRONMENT_DELETION.ENVIRONMENT_UID)
          .from(ENVIRONMENT_DELETION)
          .where(ENVIRONMENT_DELETION.LAST_CHECKED_AT.lessOrEqual(cutoff))
          .orderBy(ENVIRONMENT_DELETION.LAST_CHECKED_AT)
          .limit(limit)
          .forUpdate()
          .fetch()
          .onEach { (environmentUid) ->
            update(ENVIRONMENT_DELETION)
              .set(ENVIRONMENT_DELETION.LAST_CHECKED_AT, now)
              .where(ENVIRONMENT_DELETION.ENVIRONMENT_UID.eq(environmentUid))
              .execute()
          }
      }
    }

    return environmentUids.map { (environmentUid) ->
      sqlRetry.withRetry(READ) {
        jooq
          .selectEnvironmentColumns()
          .from(ACTIVE_ENVIRONMENT)
          .where(ACTIVE_ENVIRONMENT.UID.eq(environmentUid))
          .fetchOne { record ->
            makeEnvironment(record, objectMapper)
          }
      }
    }
  }
}
