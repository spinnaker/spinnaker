package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.ArtifactVersionStatus
import com.netflix.spinnaker.keel.api.ArtifactVersions
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.EnvironmentArtifactsSummary
import com.netflix.spinnaker.keel.api.PromotionStatus
import com.netflix.spinnaker.keel.api.PromotionStatus.APPROVED
import com.netflix.spinnaker.keel.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.api.PromotionStatus.DEPLOYING
import com.netflix.spinnaker.keel.api.PromotionStatus.PENDING
import com.netflix.spinnaker.keel.api.PromotionStatus.PREVIOUS
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_ARTIFACT_VERSIONS
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import javax.xml.bind.DatatypeConverter
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.selectOne
import org.slf4j.LoggerFactory

class SqlArtifactRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val objectMapper: ObjectMapper
) : ArtifactRepository {
  override fun register(artifact: DeliveryArtifact) {
    val id: String = jooq
      .select(DELIVERY_ARTIFACT.UID)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(artifact.name)
        .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
        .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(artifact.deliveryConfigName))
        .and(DELIVERY_ARTIFACT.REFERENCE.eq(artifact.reference)))
      .fetchOne(DELIVERY_ARTIFACT.UID)
      ?: randomUID().toString()

    jooq.insertInto(DELIVERY_ARTIFACT)
      .set(DELIVERY_ARTIFACT.UID, id)
      .set(DELIVERY_ARTIFACT.FINGERPRINT, artifact.fingerprint())
      .set(DELIVERY_ARTIFACT.NAME, artifact.name)
      .set(DELIVERY_ARTIFACT.TYPE, artifact.type.name)
      .set(DELIVERY_ARTIFACT.REFERENCE, artifact.reference)
      .set(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME, artifact.deliveryConfigName)
      .set(DELIVERY_ARTIFACT.DETAILS, artifact.detailsAsJson())
      .onDuplicateKeyUpdate()
      .set(DELIVERY_ARTIFACT.REFERENCE, artifact.reference)
      .set(DELIVERY_ARTIFACT.DETAILS, artifact.detailsAsJson())
      .execute()
  }

  private fun DeliveryArtifact.detailsAsJson() =
    when (this) {
      is DockerArtifact -> detailsAsJson()
      is DebianArtifact -> detailsAsJson()
      else -> "{}" // there are only two types of artifacts, but kotlin can't infer this here.
    }

  private fun DockerArtifact.detailsAsJson() =
    objectMapper.writeValueAsString(
      mapOf(
        "tagVersionStrategy" to tagVersionStrategy,
        "captureGroupRegex" to captureGroupRegex)
    )

  private fun DebianArtifact.detailsAsJson() =
    objectMapper.writeValueAsString(
      mapOf("statuses" to statuses)
    )

  override fun get(name: String, type: ArtifactType, deliveryConfigName: String): List<DeliveryArtifact> {
    return jooq
      .select(DELIVERY_ARTIFACT.DETAILS, DELIVERY_ARTIFACT.REFERENCE)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(name))
      .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
      .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName))
      .fetch { (details, reference) ->
        mapToArtifact(name, type, details, reference, deliveryConfigName)
      } ?: throw NoSuchArtifactException(name, type)
  }

  override fun get(name: String, type: ArtifactType, reference: String, deliveryConfigName: String): DeliveryArtifact {
    return jooq
      .select(DELIVERY_ARTIFACT.DETAILS, DELIVERY_ARTIFACT.REFERENCE)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(name))
      .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
      .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName))
      .and(DELIVERY_ARTIFACT.REFERENCE.eq(reference))
      .fetchOne()
      ?.let { (details, reference) ->
        mapToArtifact(name, type, details, reference, deliveryConfigName)
      } ?: throw NoSuchArtifactException(name, type)
  }

  override fun store(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): Boolean =
    store(artifact.name, artifact.type, version, status)

  override fun store(name: String, type: ArtifactType, version: String, status: ArtifactStatus?): Boolean {
    if (!isRegistered(name, type)) {
      throw NoSuchArtifactException(name, type)
    }

    return jooq.insertInto(ARTIFACT_VERSIONS)
      .set(ARTIFACT_VERSIONS.NAME, name)
      .set(ARTIFACT_VERSIONS.TYPE, type.value())
      .set(ARTIFACT_VERSIONS.VERSION, version)
      .set(ARTIFACT_VERSIONS.RELEASE_STATUS, status?.toString())
      .onDuplicateKeyIgnore()
      .execute() == 1
  }

  override fun isRegistered(name: String, type: ArtifactType): Boolean =
    jooq
      .selectCount()
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(name))
      .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
      .fetchOne()
      .value1() > 0

  override fun getAll(type: ArtifactType?): List<DeliveryArtifact> =
    jooq
      .select(DELIVERY_ARTIFACT.NAME, DELIVERY_ARTIFACT.TYPE, DELIVERY_ARTIFACT.DETAILS, DELIVERY_ARTIFACT.REFERENCE, DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME)
      .from(DELIVERY_ARTIFACT)
      .apply { if (type != null) where(DELIVERY_ARTIFACT.TYPE.eq(type.toString())) }
      .fetch { (name, storedType, details, reference, configName) ->
        mapToArtifact(name, ArtifactType.valueOf(storedType), details, reference, configName)
      }

  override fun versions(name: String, type: ArtifactType): List<String> {
    return jooq.select(ARTIFACT_VERSIONS.VERSION)
      .from(ARTIFACT_VERSIONS)
      .where(ARTIFACT_VERSIONS.NAME.eq(name))
      .and(ARTIFACT_VERSIONS.TYPE.eq(type.value()))
      .fetch()
      .getValues(ARTIFACT_VERSIONS.VERSION)
  }

  // todo eb: get status from artifact instead of function param?
  override fun versions(artifact: DeliveryArtifact, statuses: List<ArtifactStatus>): List<String> {
    return if (isRegistered(artifact.name, artifact.type)) {
      jooq
        .select(ARTIFACT_VERSIONS.VERSION, ARTIFACT_VERSIONS.RELEASE_STATUS)
        .from(ARTIFACT_VERSIONS)
        .where(ARTIFACT_VERSIONS.NAME.eq(artifact.name))
        .and(ARTIFACT_VERSIONS.TYPE.eq(artifact.type.value()))
        .apply { if (artifact.type == DEB && statuses.isNotEmpty()) and(ARTIFACT_VERSIONS.RELEASE_STATUS.`in`(*statuses.map { it.toString() }.toTypedArray())) }
        .fetch()
        .getValues(ARTIFACT_VERSIONS.VERSION)
        .sortedWith(artifact.versioningStrategy.comparator)
    } else {
      throw NoSuchArtifactException(artifact)
    }
  }

  override fun latestVersionApprovedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: String,
    statuses: List<ArtifactStatus>
  ): String? {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    val envUid = deliveryConfig.getUidFor(environment)
    val artifactId = artifact.uid
    val versions: List<String> = jooq
      .select(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION)
      .from(ENVIRONMENT_ARTIFACT_VERSIONS, ARTIFACT_VERSIONS)
      .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envUid))
      .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifactId))
      .and(ARTIFACT_VERSIONS.NAME.eq(artifact.name))
      .and(ARTIFACT_VERSIONS.TYPE.eq(artifact.type.name))
      .and(ARTIFACT_VERSIONS.VERSION.eq(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION))
      .apply { if (statuses.isNotEmpty()) and(ARTIFACT_VERSIONS.RELEASE_STATUS.`in`(*statuses.map { it.toString() }.toTypedArray())) }
      .orderBy(ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT.desc())
      .fetch()
      .getValues(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION)

    return versions.firstOrNull()
  }

  override fun approveVersionFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    return jooq
      .insertInto(ENVIRONMENT_ARTIFACT_VERSIONS)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID, deliveryConfig.getUidFor(environment))
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID, artifact.uid)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION, version)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT, currentTimestamp())
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, APPROVED.name)
      .onDuplicateKeyIgnore()
      .execute() > 0
  }

  override fun isApprovedFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    val versions = jooq
      .select(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION)
      .from(ENVIRONMENT_ARTIFACT_VERSIONS)
      .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(deliveryConfig.getUidFor(environment)))
      .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid))
      .fetch(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION, String::class.java)
    return versions.contains(version)
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

  override fun markAsDeployingTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    val environmentUid = deliveryConfig.getUidFor(environment)
    jooq
      .insertInto(ENVIRONMENT_ARTIFACT_VERSIONS)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID, environmentUid)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID, artifact.uid)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION, version)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, DEPLOYING.name)
      .onDuplicateKeyUpdate()
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, DEPLOYING.name)
      .execute()
  }

  override fun markAsSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    val environmentUid = deliveryConfig.getUidFor(environment)
    jooq
      .insertInto(ENVIRONMENT_ARTIFACT_VERSIONS)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID, environmentUid)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID, artifact.uid)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION, version)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT, currentTimestamp())
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, CURRENT.name)
      .onDuplicateKeyUpdate()
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT, currentTimestamp())
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, CURRENT.name)
      .execute()
    jooq
      .update(ENVIRONMENT_ARTIFACT_VERSIONS)
      .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, PREVIOUS.name)
      .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(environmentUid))
      .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid))
      .and(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.eq(CURRENT.name))
      .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.ne(version))
      .execute()
  }

  override fun versionsByEnvironment(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactsSummary> {
    return deliveryConfig.environments.map { environment ->
      val artifactVersions = deliveryConfig.artifacts.map { artifact ->
        val versionsInEnvironment = jooq
          .select(
            ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION,
            ARTIFACT_VERSIONS.RELEASE_STATUS,
            ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS
          )
          .from(
            ENVIRONMENT_ARTIFACT_VERSIONS,
            ARTIFACT_VERSIONS,
            DELIVERY_ARTIFACT,
            ENVIRONMENT,
            DELIVERY_CONFIG
          )
          .where(DELIVERY_ARTIFACT.UID.eq(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID))
          .and(DELIVERY_ARTIFACT.NAME.eq(artifact.name))
          .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
          .and(DELIVERY_ARTIFACT.REFERENCE.eq(artifact.reference))
          .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfig.name))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(ARTIFACT_VERSIONS.VERSION))
          .and(ENVIRONMENT.UID.eq(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID))
          .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))
          .and(ENVIRONMENT.NAME.eq(environment.name))
          .and(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
          .and(ARTIFACT_VERSIONS.NAME.eq(artifact.name))
          .and(ARTIFACT_VERSIONS.TYPE.eq(artifact.type.name))
        val pendingVersions = jooq
          .select(
            ARTIFACT_VERSIONS.VERSION,
            ARTIFACT_VERSIONS.RELEASE_STATUS,
            DSL.`val`(PENDING.name)
          )
          .from(
            ARTIFACT_VERSIONS,
            DELIVERY_ARTIFACT,
            ENVIRONMENT,
            DELIVERY_CONFIG
          )
          .where(DELIVERY_ARTIFACT.NAME.eq(artifact.name))
          .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
          .and(DELIVERY_ARTIFACT.REFERENCE.eq(artifact.reference))
          .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfig.name))
          .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))
          .and(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
          .and(ENVIRONMENT.NAME.eq(environment.name))
          .and(ARTIFACT_VERSIONS.NAME.eq(artifact.name))
          .and(ARTIFACT_VERSIONS.TYPE.eq(artifact.type.name))
          .andNotExists(
            selectOne()
              .from(ENVIRONMENT_ARTIFACT_VERSIONS)
              .where(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(ARTIFACT_VERSIONS.VERSION))
              .and(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
              .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(DELIVERY_ARTIFACT.UID))
          )
        val versions = versionsInEnvironment
          .unionAll(pendingVersions)
          .fetch { (version, releaseStatus, promotionStatus) ->
            Triple(version, releaseStatus, PromotionStatus.valueOf(promotionStatus))
          }
          .filter { (_, releaseStatus, _) ->
            artifact !is DebianArtifact || artifact.statuses.isEmpty() || ArtifactStatus.valueOf(releaseStatus) in artifact.statuses
          }
          .sortedWith(compareBy(artifact.versioningStrategy.comparator) { (version, _, _) -> version })
          .groupBy({ (_, _, promotionStatus) ->
            promotionStatus
          }, { (version, _, _) ->
            version
          })
        ArtifactVersions(
          name = artifact.name,
          type = artifact.type,
          versions = ArtifactVersionStatus(
            current = versions[CURRENT]?.firstOrNull(),
            deploying = versions[DEPLOYING]?.firstOrNull(),
            pending = versions[PENDING] ?: emptyList(),
            previous = versions[PREVIOUS] ?: emptyList()
          )
        )
      }
      EnvironmentArtifactsSummary(environment.name, artifactVersions)
    }
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
        .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
        .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName))
        .and(DELIVERY_ARTIFACT.REFERENCE.eq(reference)))

  private val DeliveryConfig.uid: Select<Record1<String>>
    get() = select(DELIVERY_CONFIG.UID)
      .from(DELIVERY_CONFIG)
      // TODO: currently this is unique but I feel like it should be a compound key with application name
      .where(DELIVERY_CONFIG.NAME.eq(name))

  // Generates a unique hash for an artifact
  private fun DeliveryArtifact.fingerprint(): String {
    return fingerprint(name, type.name, deliveryConfigName ?: "_pending", reference)
  }

  private fun fingerprint(name: String, type: String, deliveryConfigName: String, reference: String): String {
    val data = name + type + deliveryConfigName + reference
    val bytes = MessageDigest
      .getInstance("SHA-1")
      .digest(data.toByteArray())
    return DatatypeConverter.printHexBinary(bytes).toUpperCase()
  }

  private fun Instant.toLocal() = atZone(clock.zone).toLocalDateTime()

  private fun currentTimestamp() = clock.instant().toLocal()

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
