package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.api.verification.VerificationState
import com.netflix.spinnaker.keel.api.verification.VerificationStatus
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.VERIFICATION_STATE
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.ResultQuery
import org.jooq.Select
import java.time.Clock

class SqlVerificationRepository(
  private val jooq: DSLContext,
  private val clock: Clock
) : VerificationRepository {

  override fun getState(
    context: VerificationContext,
    verification: Verification
  ): VerificationState? =
    with(context) {
      jooq
        .select(
          VERIFICATION_STATE.STATUS,
          VERIFICATION_STATE.STARTED_AT,
          VERIFICATION_STATE.ENDED_AT
        )
        .from(VERIFICATION_STATE)
        .where(VERIFICATION_STATE.ENVIRONMENT_UID.eq(environmentUid))
        .and(VERIFICATION_STATE.ARTIFACT_UID.eq(artifact.uid))
        .and(VERIFICATION_STATE.ARTIFACT_VERSION.eq(version))
        .and(VERIFICATION_STATE.VERIFICATION_ID.eq(verification.id))
        .fetchOneInto<VerificationState>()
    }

  override fun updateState(
    context: VerificationContext,
    verification: Verification,
    status: VerificationStatus
  ) {
    with(context) {
      jooq
        .insertInto(VERIFICATION_STATE)
        .set(VERIFICATION_STATE.STATUS, status.name)
        .set(status.timestampColumn, currentTimestamp())
        .set(VERIFICATION_STATE.ENVIRONMENT_UID, environmentUid)
        .set(VERIFICATION_STATE.ARTIFACT_UID, artifact.uid)
        .set(VERIFICATION_STATE.ARTIFACT_VERSION, version)
        .set(VERIFICATION_STATE.VERIFICATION_ID, verification.id)
        .onDuplicateKeyUpdate()
        .set(VERIFICATION_STATE.STATUS, status.name)
        .set(status.timestampColumn, currentTimestamp())
        .execute()
    }
  }

  private inline fun <reified RESULT> ResultQuery<*>.fetchOneInto() =
    fetchOneInto(RESULT::class.java)

  private fun currentTimestamp() = clock.instant().toTimestamp()

  private val VerificationStatus.timestampColumn
    get() = if (complete) VERIFICATION_STATE.ENDED_AT else VERIFICATION_STATE.STARTED_AT

  private val VerificationContext.environmentUid: Select<Record1<String>>
    get() = jooq
      .select(ENVIRONMENT.UID)
      .from(DELIVERY_CONFIG, ENVIRONMENT)
      .where(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
      .and(ENVIRONMENT.NAME.eq(environment.name))
      .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))

  private val DeliveryArtifact.uid: Select<Record1<String>>
    get() = jooq
      .select(DELIVERY_ARTIFACT.UID)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(name))
      .and(DELIVERY_ARTIFACT.TYPE.eq(type))
}
