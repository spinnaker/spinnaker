package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.persistence.PromotionRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_ARTIFACT_VERSIONS
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL.select
import java.time.Clock
import java.time.Instant

class SqlPromotionRepository(
  private val jooq: DSLContext,
  private val clock: Clock
) : PromotionRepository {
  override fun latestVersionApprovedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: String
  ): String? {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    return jooq
      .select(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION)
      .from(ENVIRONMENT_ARTIFACT_VERSIONS)
      .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(deliveryConfig.getUidFor(environment)))
      .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid))
      .orderBy(ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT.desc())
      .limit(1)
      .fetchOne(0, String::class.java)
  }

  override fun approveVersionFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    jooq
      .insertInto(ENVIRONMENT_ARTIFACT_VERSIONS)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID, deliveryConfig.getUidFor(environment))
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID, artifact.uid)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION, version)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT, currentTimestamp())
      .onDuplicateKeyUpdate()
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT, currentTimestamp())
      .execute()
  }

  override fun wasSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    return jooq
      .fetchExists(
        ENVIRONMENT_ARTIFACT_VERSIONS,
        ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(deliveryConfig.getUidFor(environment))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(version))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT.isNotNull)
      )
  }

  override fun markAsSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    jooq
      .insertInto(ENVIRONMENT_ARTIFACT_VERSIONS)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID, deliveryConfig.getUidFor(environment))
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID, artifact.uid)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION, version)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT, currentTimestamp())
      .onDuplicateKeyUpdate()
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT, currentTimestamp())
      .execute()
  }

  private fun DeliveryConfig.environmentNamed(name: String): Environment =
    requireNotNull(environments.firstOrNull { it.name == name }) {
      "No environment named $name exists in the configuration ${this.name}"
    }

  private fun DeliveryConfig.getUidFor(environment: Environment): Select<Record1<String>> =
    select(ENVIRONMENT.UID)
      .from(ENVIRONMENT)
      .where(ENVIRONMENT.NAME.eq(environment.name))
      .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(uid))

  private val DeliveryArtifact.uid: Select<Record1<String>>
    get() = select(DELIVERY_ARTIFACT.UID)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(name)
        .and(DELIVERY_ARTIFACT.TYPE.eq(type.name)))

  private val DeliveryConfig.uid: Select<Record1<String>>
    get() = select(DELIVERY_CONFIG.UID)
      .from(DELIVERY_CONFIG)
      // TODO: currently this is unique but I feel like it should be a compound key with application name
      .where(DELIVERY_CONFIG.NAME.eq(name))

  private fun Instant.toLocal() = atZone(clock.zone).toLocalDateTime()

  private fun currentTimestamp() = clock.instant().toLocal()
}
