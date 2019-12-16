package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.ArtifactVersionStatus
import java.time.LocalDateTime
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.ArtifactVersions
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.EnvironmentArtifactsSummary
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT_VERSION
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_ARTIFACT_VERSIONS
import java.time.Clock
import java.time.Instant
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL.castNull
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.selectOne
import org.slf4j.LoggerFactory

class SqlArtifactRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val objectMapper: ObjectMapper
) : ArtifactRepository {
  override fun register(artifact: DeliveryArtifact) {
    jooq.insertInto(DELIVERY_ARTIFACT)
      .set(DELIVERY_ARTIFACT.UID, randomUID().toString())
      .set(DELIVERY_ARTIFACT.NAME, artifact.name)
      .set(DELIVERY_ARTIFACT.TYPE, artifact.type.name)
      .set(DELIVERY_ARTIFACT.DETAILS, artifact.detailsAsJson())
      .onDuplicateKeyUpdate()
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

  override fun get(name: String, type: ArtifactType): DeliveryArtifact {
    return jooq
      .select(DELIVERY_ARTIFACT.DETAILS)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(name))
      .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
      .fetchOne()
      ?.let { (details) ->
        mapToArtifact(name, type, details)
      } ?: throw NoSuchArtifactException(name, type)
  }

  override fun store(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): Boolean {
    val uid = jooq.select(DELIVERY_ARTIFACT.UID)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(artifact.name))
      .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
      .fetchOne()
      ?: throw NoSuchArtifactException(artifact)

    return jooq.insertInto(DELIVERY_ARTIFACT_VERSION)
      .set(DELIVERY_ARTIFACT_VERSION.DELIVERY_ARTIFACT_UID, uid.value1())
      .set(DELIVERY_ARTIFACT_VERSION.VERSION, version)
      .set(DELIVERY_ARTIFACT_VERSION.STATUS, status?.toString())
      .onDuplicateKeyIgnore()
      .execute() == 1
  }

  override fun isRegistered(name: String, type: ArtifactType): Boolean =
    jooq
      .selectOne()
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(name))
      .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
      .fetchOne() != null

  override fun getAll(type: ArtifactType?): List<DeliveryArtifact> =
    jooq
      .select(DELIVERY_ARTIFACT.NAME, DELIVERY_ARTIFACT.TYPE, DELIVERY_ARTIFACT.DETAILS)
      .from(DELIVERY_ARTIFACT)
      .apply { if (type != null) where(DELIVERY_ARTIFACT.TYPE.eq(type.toString())) }
      .fetch { (name, storedType, details) ->
        mapToArtifact(name, ArtifactType.valueOf(storedType), details)
      }

  override fun versions(name: String, type: ArtifactType, statuses: List<ArtifactStatus>): List<String> =
    versions(get(name, type), statuses)

  override fun versions(artifact: DeliveryArtifact, statuses: List<ArtifactStatus>): List<String> {
    return if (isRegistered(artifact.name, artifact.type)) {
      jooq
        .select(DELIVERY_ARTIFACT_VERSION.VERSION)
        .from(DELIVERY_ARTIFACT, DELIVERY_ARTIFACT_VERSION)
        .where(DELIVERY_ARTIFACT.UID.eq(DELIVERY_ARTIFACT_VERSION.DELIVERY_ARTIFACT_UID))
        .and(DELIVERY_ARTIFACT.NAME.eq(artifact.name))
        .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
        .apply { if (artifact.type == DEB && statuses.isNotEmpty()) and(DELIVERY_ARTIFACT_VERSION.STATUS.`in`(*statuses.map { it.toString() }.toTypedArray())) }
        .fetch()
        .getValues(DELIVERY_ARTIFACT_VERSION.VERSION)
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
    return jooq
      .select(DELIVERY_ARTIFACT_VERSION.VERSION)
      .from(ENVIRONMENT_ARTIFACT_VERSIONS
        .innerJoin(DELIVERY_ARTIFACT_VERSION)
        .on(DELIVERY_ARTIFACT_VERSION.DELIVERY_ARTIFACT_UID.eq(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID)))
      .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envUid))
      .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(DELIVERY_ARTIFACT_VERSION.VERSION))
      .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifactId))
      .apply { if (statuses.isNotEmpty()) and(DELIVERY_ARTIFACT_VERSION.STATUS.`in`(*statuses.map { it.toString() }.toTypedArray())) }
      .orderBy(ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT.desc())
      .limit(1)
      .fetchOne(0, String::class.java)
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

  override fun versionsByEnvironment(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactsSummary> {
    return deliveryConfig.environments.map { environment ->
      val artifactVersions = deliveryConfig.artifacts.map { artifact ->
        val versionsInEnvironment = jooq
          .select(
            ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION,
            ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT,
            ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT
          )
          .from(
            ENVIRONMENT_ARTIFACT_VERSIONS,
            DELIVERY_ARTIFACT,
            ENVIRONMENT,
            DELIVERY_CONFIG
          )
          .where(DELIVERY_ARTIFACT.UID.eq(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID))
          .and(DELIVERY_ARTIFACT.NAME.eq(artifact.name))
          .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
          .and(ENVIRONMENT.UID.eq(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID))
          .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))
          .and(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
          .and(ENVIRONMENT.NAME.eq(environment.name))
        val pendingVersions = jooq
          .select(
            DELIVERY_ARTIFACT_VERSION.VERSION,
            castNull(LocalDateTime::class.java),
            castNull(LocalDateTime::class.java)
          )
          .from(
            DELIVERY_ARTIFACT_VERSION,
            DELIVERY_ARTIFACT,
            ENVIRONMENT,
            DELIVERY_CONFIG
          )
          .where(DELIVERY_ARTIFACT.UID.eq(DELIVERY_ARTIFACT_VERSION.DELIVERY_ARTIFACT_UID))
          .and(DELIVERY_ARTIFACT.NAME.eq(artifact.name))
          .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
          .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))
          .and(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
          .and(ENVIRONMENT.NAME.eq(environment.name))
          .andNotExists(
            selectOne()
              .from(ENVIRONMENT_ARTIFACT_VERSIONS)
              .where(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(DELIVERY_ARTIFACT_VERSION.VERSION))
              .and(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
          )
        val versions = versionsInEnvironment
          .unionAll(pendingVersions)
          .fetch()
          .map { (version, approvedAt, deployedAt) ->
            ArtifactVersionResult(version, approvedAt, deployedAt)
          }
        val deployed = versions.filter { it.deployedAt != null }
        val current = deployed.maxBy { checkNotNull(it.deployedAt) }
        val previous = deployed.filterNot { it == current }
        val deploying = versions.filter { it.approvedAt != null && it.deployedAt == null }.maxBy { checkNotNull(it.approvedAt) }
        val pending = versions.filter { it.approvedAt == null }
        ArtifactVersions(
          name = artifact.name,
          type = artifact.type,
          versions = ArtifactVersionStatus(
            current = current?.version,
            deploying = deploying?.version,
            pending = pending.map { it.version },
            previous = previous.map { it.version }
          )
        )
      }
      EnvironmentArtifactsSummary(environment.name, artifactVersions)
    }
  }

  private data class ArtifactVersionResult(
    val version: String,
    val approvedAt: LocalDateTime?,
    val deployedAt: LocalDateTime?
  )

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

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
