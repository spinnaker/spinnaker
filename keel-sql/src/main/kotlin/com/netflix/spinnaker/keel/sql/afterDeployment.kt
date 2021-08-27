package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.pause.PauseScope
import com.netflix.spinnaker.keel.persistence.metamodel.Tables
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTIVE_ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_LAST_VERIFIED
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectForUpdateStep
import org.jooq.SelectOptionStep
import org.jooq.impl.DSL
import java.time.Instant

internal fun DSLContext.nextEnvironmentQuery(
  cutoff: Instant,
  limit: Int
) =
  select(
    DELIVERY_CONFIG.UID,
    DELIVERY_CONFIG.NAME,
    ACTIVE_ENVIRONMENT.UID,
    ACTIVE_ENVIRONMENT.NAME,
    DELIVERY_ARTIFACT.UID,
    DELIVERY_ARTIFACT.REFERENCE,
    ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION
  )
    .from(ACTIVE_ENVIRONMENT)
    .join(DELIVERY_CONFIG)
    .on(DELIVERY_CONFIG.UID.eq(ACTIVE_ENVIRONMENT.DELIVERY_CONFIG_UID))
    // the application is not paused
    .andNotExists(
      selectOne()
        .from(Tables.PAUSED)
        .where(Tables.PAUSED.NAME.eq(DELIVERY_CONFIG.APPLICATION))
        .and(Tables.PAUSED.SCOPE.eq(PauseScope.APPLICATION))
    )
    // join currently deployed artifact version
    .join(ENVIRONMENT_ARTIFACT_VERSIONS)
    .on(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(ACTIVE_ENVIRONMENT.UID))
    .and(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.eq(CURRENT))
    // join artifact
    .join(DELIVERY_ARTIFACT)
    .on(DELIVERY_ARTIFACT.UID.eq(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID))
    // left join so we get results even if there is no row in ENVIRONMENT_LAST_VERIFIED
    .leftJoin(ENVIRONMENT_LAST_VERIFIED)
    .on(ENVIRONMENT_LAST_VERIFIED.ENVIRONMENT_UID.eq(ACTIVE_ENVIRONMENT.UID))
    .and(ENVIRONMENT_LAST_VERIFIED.ARTIFACT_UID.eq(DELIVERY_ARTIFACT.UID))
    .and(ENVIRONMENT_LAST_VERIFIED.ARTIFACT_VERSION.eq(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION))
    // has not been checked recently (or has never been checked)
    .where(DSL.isnull(ENVIRONMENT_LAST_VERIFIED.AT, Instant.EPOCH).lessOrEqual(cutoff))
    // order by last time checked with things never checked coming first
    .orderBy(DSL.isnull(ENVIRONMENT_LAST_VERIFIED.AT, Instant.EPOCH))
    .limit(limit)


/**
 * Set a share mode lock on a select query to prevent phantom reads in a transaction.
 *
 * In MySQL 5.7, this is `LOCK IN SHARE MODE`
 * See https://dev.mysql.com/doc/refman/5.7/en/innodb-locking-reads.html
 */
fun <R : Record?> SelectForUpdateStep<R>.lockInShareMode(useLockingRead: Boolean): SelectOptionStep<R> =
  if(useLockingRead) {
    this.forShare()
  } else {
    this
  }
