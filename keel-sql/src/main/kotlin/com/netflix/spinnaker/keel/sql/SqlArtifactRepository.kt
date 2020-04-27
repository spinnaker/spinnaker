package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.TagComparator
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.ArtifactVersionStatus
import com.netflix.spinnaker.keel.core.api.ArtifactVersions
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.Pinned
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.core.api.PromotionStatus.APPROVED
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.DEPLOYING
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PENDING
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PREVIOUS
import com.netflix.spinnaker.keel.core.api.PromotionStatus.SKIPPED
import com.netflix.spinnaker.keel.core.api.PromotionStatus.VETOED
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.core.comparator
import com.netflix.spinnaker.keel.exceptions.InvalidRegexException
import com.netflix.spinnaker.keel.persistence.ArtifactNotFoundException
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ARTIFACT_LAST_CHECKED
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_ARTIFACT_PIN
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH
import java.time.ZoneOffset
import javax.xml.bind.DatatypeConverter
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.selectOne
import org.jooq.util.mysql.MySQLDSL
import org.slf4j.LoggerFactory

class SqlArtifactRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val objectMapper: ObjectMapper,
  private val sqlRetry: SqlRetry
) : ArtifactRepository {
  override fun register(artifact: DeliveryArtifact) {
    val id: String = (sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_ARTIFACT.UID)
        .from(DELIVERY_ARTIFACT)
        .where(DELIVERY_ARTIFACT.NAME.eq(artifact.name)
          .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
          .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(artifact.deliveryConfigName))
          .and(DELIVERY_ARTIFACT.REFERENCE.eq(artifact.reference)))
        .fetchOne(DELIVERY_ARTIFACT.UID)
    }
      ?: randomUID().toString())

    sqlRetry.withRetry(WRITE) {
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
      jooq.insertInto(ARTIFACT_LAST_CHECKED)
        .set(ARTIFACT_LAST_CHECKED.ARTIFACT_UID, id)
        .set(ARTIFACT_LAST_CHECKED.AT, EPOCH.plusSeconds(1).toLocal())
        .onDuplicateKeyUpdate()
        .set(ARTIFACT_LAST_CHECKED.AT, EPOCH.plusSeconds(1).toLocal())
        .execute()
    }
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
      mapOf(
        "vmOptions" to vmOptions,
        "statuses" to statuses
      )
    )

  override fun get(name: String, type: ArtifactType, deliveryConfigName: String): List<DeliveryArtifact> {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_ARTIFACT.DETAILS, DELIVERY_ARTIFACT.REFERENCE)
        .from(DELIVERY_ARTIFACT)
        .where(DELIVERY_ARTIFACT.NAME.eq(name))
        .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
        .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName))
        .fetch { (details, reference) ->
          mapToArtifact(name, type, details, reference, deliveryConfigName)
        }
    } ?: throw NoSuchArtifactException(name, type)
  }

  override fun get(name: String, type: ArtifactType, reference: String, deliveryConfigName: String): DeliveryArtifact {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_ARTIFACT.DETAILS, DELIVERY_ARTIFACT.REFERENCE)
        .from(DELIVERY_ARTIFACT)
        .where(DELIVERY_ARTIFACT.NAME.eq(name))
        .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
        .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName))
        .and(DELIVERY_ARTIFACT.REFERENCE.eq(reference))
        .fetchOne()
    }
      ?.let { (details, reference) ->
        mapToArtifact(name, type, details, reference, deliveryConfigName)
      } ?: throw ArtifactNotFoundException(reference, deliveryConfigName)
  }

  override fun get(deliveryConfigName: String, reference: String): DeliveryArtifact {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_ARTIFACT.NAME, DELIVERY_ARTIFACT.DETAILS, DELIVERY_ARTIFACT.REFERENCE, DELIVERY_ARTIFACT.TYPE)
        .from(DELIVERY_ARTIFACT)
        .where(
          DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName),
          DELIVERY_ARTIFACT.REFERENCE.eq(reference)
        )
        .fetchOne()
    }
      ?.let { (name, details, reference, type) ->
        mapToArtifact(name, ArtifactType.valueOf(type), details, reference, deliveryConfigName)
      } ?: throw ArtifactNotFoundException(reference, deliveryConfigName)
  }

  override fun store(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): Boolean =
    store(artifact.name, artifact.type, version, status)

  override fun store(name: String, type: ArtifactType, version: String, status: ArtifactStatus?): Boolean {
    if (!isRegistered(name, type)) {
      throw NoSuchArtifactException(name, type)
    }

    return sqlRetry.withRetry(WRITE) {
      jooq.insertInto(ARTIFACT_VERSIONS)
        .set(ARTIFACT_VERSIONS.NAME, name)
        .set(ARTIFACT_VERSIONS.TYPE, type.name)
        .set(ARTIFACT_VERSIONS.VERSION, version)
        .set(ARTIFACT_VERSIONS.RELEASE_STATUS, status?.toString())
        .onDuplicateKeyIgnore()
        .execute()
    } == 1
  }

  override fun delete(artifact: DeliveryArtifact) {
    requireNotNull(artifact.deliveryConfigName) { "Error removing artifact - it has no delivery config!" }
    val deliveryConfigId = select(DELIVERY_CONFIG.UID)
      .from(DELIVERY_CONFIG)
      .where(DELIVERY_CONFIG.NAME.eq(artifact.deliveryConfigName))

    jooq.transaction { config ->
      val txn = DSL.using(config)
      txn.deleteFrom(DELIVERY_CONFIG_ARTIFACT)
        .where(DELIVERY_CONFIG_ARTIFACT.DELIVERY_CONFIG_UID.eq(deliveryConfigId))
        .and(DELIVERY_CONFIG_ARTIFACT.ARTIFACT_UID.eq(artifact.uid))
        .execute()
      txn.deleteFrom(DELIVERY_ARTIFACT)
        .where(DELIVERY_ARTIFACT.UID.eq(artifact.uid))
        .execute()
    }
  }

  override fun isRegistered(name: String, type: ArtifactType): Boolean =
    sqlRetry.withRetry(READ) {
      jooq
        .selectCount()
        .from(DELIVERY_ARTIFACT)
        .where(DELIVERY_ARTIFACT.NAME.eq(name))
        .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
        .fetchOne()
        .value1()
    } > 0

  override fun getAll(type: ArtifactType?): List<DeliveryArtifact> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_ARTIFACT.NAME, DELIVERY_ARTIFACT.TYPE, DELIVERY_ARTIFACT.DETAILS, DELIVERY_ARTIFACT.REFERENCE, DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME)
        .from(DELIVERY_ARTIFACT)
        .apply { if (type != null) where(DELIVERY_ARTIFACT.TYPE.eq(type.toString())) }
        .fetch { (name, storedType, details, reference, configName) ->
          mapToArtifact(name, ArtifactType.valueOf(storedType.toLowerCase()), details, reference, configName)
        }
    }

  override fun versions(name: String, type: ArtifactType): List<String> {
    return sqlRetry.withRetry(READ) {
      jooq.select(ARTIFACT_VERSIONS.VERSION)
        .from(ARTIFACT_VERSIONS)
        .where(ARTIFACT_VERSIONS.NAME.eq(name))
        .and(ARTIFACT_VERSIONS.TYPE.eq(type.name))
        .fetch()
        .getValues(ARTIFACT_VERSIONS.VERSION)
    }
  }

  override fun versions(artifact: DeliveryArtifact): List<String> {
    return if (isRegistered(artifact.name, artifact.type)) {
      sqlRetry.withRetry(READ) {
        jooq
          .select(ARTIFACT_VERSIONS.VERSION, ARTIFACT_VERSIONS.RELEASE_STATUS)
          .from(ARTIFACT_VERSIONS)
          .where(ARTIFACT_VERSIONS.NAME.eq(artifact.name))
          .and(ARTIFACT_VERSIONS.TYPE.eq(artifact.type.name))
          .apply { if (artifact is DebianArtifact && artifact.statuses.isNotEmpty()) and(ARTIFACT_VERSIONS.RELEASE_STATUS.`in`(*artifact.statuses.map { it.toString() }.toTypedArray())) }
          .fetch()
          .getValues(ARTIFACT_VERSIONS.VERSION)
      }
        .sortedWith(artifact.versioningStrategy.comparator)
        .also { versions ->
          return if (artifact is DockerArtifact) {
            filterDockerVersions(artifact, versions)
          } else {
            versions
          }
        }
    } else {
      throw NoSuchArtifactException(artifact)
    }
  }

  /**
   * Given a docker artifact and a list of docker tags, filters out all tags that don't produce exactly one capture
   * group with the provided regex.
   *
   * This means that this will filter out tags like "latest" from the list.
   */
  private fun filterDockerVersions(artifact: DockerArtifact, versions: List<String>): List<String> =
    versions.filter { shouldInclude(it, artifact) }

  /**
   * Returns true if a docker tag is not latest and the regex produces exactly one capture group on the tag, false otherwise.
   */
  private fun shouldInclude(tag: String, artifact: DockerArtifact) =
    try {
      tag != "latest" && TagComparator.parseWithRegex(tag, artifact.tagVersionStrategy, artifact.captureGroupRegex) != null
    } catch (e: InvalidRegexException) {
      log.warn("Version $tag produced more than one capture group based on artifact $artifact, excluding")
      false
    }

  override fun latestVersionApprovedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: String
  ): String? {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    val envUid = deliveryConfig.getUidFor(environment)
    val artifactId = artifact.uid

    /**
     * If [targetEnvironment] has been pinned to an artifact version, return
     * the pinned version. Otherwise return the most recently approved version.
     */
    return sqlRetry.withRetry(READ) {
      jooq.select(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_VERSION)
        .from(ENVIRONMENT_ARTIFACT_PIN)
        .where(
          ENVIRONMENT_ARTIFACT_PIN.ENVIRONMENT_UID.eq(envUid),
          ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_UID.eq(artifactId))
        .fetchOne(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_VERSION)
        ?: jooq
          .select(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION)
          .from(ENVIRONMENT_ARTIFACT_VERSIONS)
          .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envUid))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifactId))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT.isNotNull)
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.ne(VETOED.name))
          .orderBy(ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT.desc())
          .limit(20)
          .fetch(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION)
          .let {
            when (it.isNotEmpty()) {
              true -> it
                .sortedWith(artifact.versioningStrategy.comparator)
                .first()
              else -> null
            }
          }
    }
  }

  override fun approveVersionFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    return sqlRetry.withRetry(WRITE) {
      jooq
        .insertInto(ENVIRONMENT_ARTIFACT_VERSIONS)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID, deliveryConfig.getUidFor(environment))
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID, artifact.uid)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION, version)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT, currentTimestamp())
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, APPROVED.name)
        .onDuplicateKeyIgnore()
        .execute()
    } > 0
  }

  override fun isApprovedFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    return sqlRetry.withRetry(READ) {
      jooq
        .fetchExists(
          ENVIRONMENT_ARTIFACT_VERSIONS,
          ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(deliveryConfig.getUidFor(environment))
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid))
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(version))
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.ne(VETOED.name))
        )
    }
  }

  override fun wasSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    return sqlRetry.withRetry(READ) {
      jooq
        .fetchExists(
          ENVIRONMENT_ARTIFACT_VERSIONS,
          ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(deliveryConfig.getUidFor(environment))
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid))
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(version))
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT.isNotNull)
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.ne(VETOED.name))
        )
    }
  }

  override fun isCurrentlyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    return sqlRetry.withRetry(READ) {
      jooq
        .fetchExists(
          ENVIRONMENT_ARTIFACT_VERSIONS,
          ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(deliveryConfig.getUidFor(environment))
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid))
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(version))
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT.isNotNull)
            .and(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.eq(CURRENT.name))
        )
    }
  }

  override fun markAsDeployingTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    val environmentUid = deliveryConfig.getUidFor(environment)
    sqlRetry.withRetry(WRITE) {
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
  }

  override fun markAsSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    val environmentUid = deliveryConfig.getUidFor(environment)
    sqlRetry.withRetry(WRITE) {
      jooq.transaction { config ->
        val txn = DSL.using(config)
        txn
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
        // update old "CURRENT" to "PREVIOUS
        txn
          .update(ENVIRONMENT_ARTIFACT_VERSIONS)
          .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, PREVIOUS.name)
          .set(ENVIRONMENT_ARTIFACT_VERSIONS.REPLACED_BY, version)
          .set(ENVIRONMENT_ARTIFACT_VERSIONS.REPLACED_AT, currentTimestamp())
          .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(environmentUid))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.eq(CURRENT.name))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.ne(version))
          .execute()
        // update any past artifacts that were "APPROVED" to be "SKIPPED"
        // because the new version takes precedence
        val approvedButOld = txn.select(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION)
          .from(ENVIRONMENT_ARTIFACT_VERSIONS)
          .where(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.eq(APPROVED.name))
          .fetch(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION)
          .filter { isOlder(artifact, it, version) }

        txn
          .update(ENVIRONMENT_ARTIFACT_VERSIONS)
          .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, SKIPPED.name)
          .set(ENVIRONMENT_ARTIFACT_VERSIONS.REPLACED_BY, version)
          .set(ENVIRONMENT_ARTIFACT_VERSIONS.REPLACED_AT, currentTimestamp())
          .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(environmentUid))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.eq(APPROVED.name))
          .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.`in`(*approvedButOld.toTypedArray()))
          .execute()
      }
    }
  }

  override fun vetoedEnvironmentVersions(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactVetoes> {
    val artifactsById = deliveryConfig.artifacts
      .associateBy { it.uidString }

    val vetoes: MutableMap<String, EnvironmentArtifactVetoes> = mutableMapOf()

    jooq.select(
      ENVIRONMENT.NAME,
      ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID,
      ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION
    )
      .from(ENVIRONMENT)
      .innerJoin(ENVIRONMENT_ARTIFACT_VERSIONS)
      .on(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
      .where(
        ENVIRONMENT.DELIVERY_CONFIG_UID.eq(deliveryConfig.uid),
        ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.eq(VETOED.name)
      )
      .fetch { (envName, artifactId, version) ->
        if (artifactsById.containsKey(artifactId)) {
          vetoes.getOrPut(vetoKey(envName, artifactId),
            {
              EnvironmentArtifactVetoes(
                deliveryConfigName = deliveryConfig.name,
                targetEnvironment = envName,
                artifact = artifactsById[artifactId]
                  ?: error("Invalid artifactId $artifactId for deliveryConfig ${deliveryConfig.name}"),
                versions = mutableSetOf())
            })
            .versions.add(version)
        }
      }

    return vetoes.values.toList()
  }

  override fun markAsVetoedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String,
    force: Boolean
  ): Boolean {

    val (envUid, artUid) = sqlRetry.withRetry(READ) {
      Pair(
        deliveryConfig.getUidStringFor(
          deliveryConfig.environmentNamed(targetEnvironment)),
        artifact.uidString)
    }

    val isPinned = sqlRetry.withRetry(READ) {
      jooq
        .fetchExists(
          ENVIRONMENT_ARTIFACT_PIN,
          ENVIRONMENT_ARTIFACT_PIN.ENVIRONMENT_UID.eq(envUid)
            .and(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_UID.eq(artUid))
            .and(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_VERSION.eq(version)))
    }

    if (isPinned) {
      log.warn(
        "Pinned artifact version cannot be vetoed: " +
          "deliveryConfig=${deliveryConfig.name}, " +
          "environment=$targetEnvironment, " +
          "artifactVersion=$version")
      return false
    }

    return jooq
      .select(
        ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS,
        ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_REFERENCE)
      .from(ENVIRONMENT_ARTIFACT_VERSIONS)
      .where(
        ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envUid),
        ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artUid),
        ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(version))
      .fetchOne { (_, reference) ->
        if (reference != null && !force) {
          log.warn(
            "Not vetoing artifact version as it appears to have already been an automated rollback target: " +
              "deliveryConfig=${deliveryConfig.name}, " +
              "environment=$targetEnvironment, " +
              "artifactVersion=$version, " +
              "priorVersionReference=$reference")
          return@fetchOne false
        }

        val prior = priorVersionDeployedIn(envUid, artUid, version)

        sqlRetry.withRetry(WRITE) {
          jooq.transaction { config ->
            val txn = DSL.using(config)
            txn.update(ENVIRONMENT_ARTIFACT_VERSIONS)
              .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, VETOED.name)
              .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_REFERENCE, prior ?: version)
              .where(
                ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envUid),
                ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artUid),
                ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(version)
              )
              .execute()

            /**
             * If there's a previously deployed version in [targetEnvironment], set `promotion_reference`
             * to the version that's currently being vetoed. If that version also fails to fully deploy,
             * this is used to short-circuit further automated vetoes. We want to avoid a cloud provider
             * or other issue unrelated to an artifact version triggering continual automated rollbacks
             * thru all previously deployed versions.
             */
            if (prior != null) {
              txn.update(ENVIRONMENT_ARTIFACT_VERSIONS)
                .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_REFERENCE, version)
                .where(
                  ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envUid),
                  ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artUid),
                  ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(prior)
                )
                .execute()
            }
          }
        }
        return@fetchOne true
      }
  }

  override fun deleteVeto(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val envId = deliveryConfig.getUidFor(
      deliveryConfig.environmentNamed(targetEnvironment))

    sqlRetry.withRetry(WRITE) {
      val referenceVersion: String? = jooq.select(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_REFERENCE)
        .from(ENVIRONMENT_ARTIFACT_VERSIONS)
        .where(
          ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envId),
          ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid),
          ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(version))
        .fetchOne(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_REFERENCE)

      /**
       * If there are bidirectional `PROMOTION_REFERENCE` markers between this [version]
       * and another (i.e. the veto was applied in order to rollback from this version
       * to the other), both sides are removed.
       */
      val referencesReferenceVersion: String? = when (referenceVersion) {
        null -> null
        else -> {
          jooq.select(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_REFERENCE)
            .from(ENVIRONMENT_ARTIFACT_VERSIONS)
            .where(
              ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envId),
              ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid),
              ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(referenceVersion))
            .fetchOne(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_REFERENCE)
        }
      }

      jooq.transaction { config ->
        val txn = DSL.using(config)

        txn.update(ENVIRONMENT_ARTIFACT_VERSIONS)
          .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, APPROVED.name)
          .setNull(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_REFERENCE)
          .where(
            ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envId),
            ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid),
            ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(version))
          .execute()

        if (referencesReferenceVersion != null && referenceVersion == referencesReferenceVersion) {
          txn.update(ENVIRONMENT_ARTIFACT_VERSIONS)
            .setNull(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_REFERENCE)
            .where(
              ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envId),
              ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid),
              ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(referenceVersion))
            .execute()
        }
      }
    }
  }

  override fun markAsSkipped(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String,
    supersededByVersion: String
  ) {
    val environment = deliveryConfig.environmentNamed(targetEnvironment)
    val environmentUid = deliveryConfig.getUidFor(environment)
    sqlRetry.withRetry(WRITE) {
      jooq
        .insertInto(ENVIRONMENT_ARTIFACT_VERSIONS)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID, environmentUid)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID, artifact.uid)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION, version)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, SKIPPED.name)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.REPLACED_BY, supersededByVersion)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.REPLACED_AT, currentTimestamp())
        .onDuplicateKeyUpdate()
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS, SKIPPED.name)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.REPLACED_BY, supersededByVersion)
        .set(ENVIRONMENT_ARTIFACT_VERSIONS.REPLACED_AT, currentTimestamp())
        .execute()
    }
  }

  override fun getEnvironmentSummaries(deliveryConfig: DeliveryConfig): List<EnvironmentSummary> {
    val pinnedEnvs = getPinnedEnvironments(deliveryConfig)
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
          .apply { if (artifact is DebianArtifact && artifact.statuses.isNotEmpty()) and(ARTIFACT_VERSIONS.RELEASE_STATUS.`in`(*artifact.statuses.map { it.toString() }.toTypedArray())) }
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
          .apply { if (artifact is DebianArtifact && artifact.statuses.isNotEmpty()) and(ARTIFACT_VERSIONS.RELEASE_STATUS.`in`(*artifact.statuses.map { it.toString() }.toTypedArray())) }
          .andNotExists(
            selectOne()
              .from(ENVIRONMENT_ARTIFACT_VERSIONS)
              .where(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(ARTIFACT_VERSIONS.VERSION))
              .and(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
              .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(DELIVERY_ARTIFACT.UID))
          )
        val unionedVersions = sqlRetry.withRetry(READ) {
          versionsInEnvironment
            .unionAll(pendingVersions)
            .fetch { (version, releaseStatus, promotionStatus) ->
              Triple(version, releaseStatus, PromotionStatus.valueOf(promotionStatus))
            }
        }
          .filter { (version, _, _) ->
            if (artifact is DockerArtifact) {
              // filter out invalid docker tags
              shouldInclude(version, artifact)
            } else {
              true
            }
          }

        val releaseStatuses: Set<ArtifactStatus> = unionedVersions
          .filter { (_, releaseStatus, _) ->
            releaseStatus != null
          }
          .map { (_, releaseStatus, _) ->
            ArtifactStatus.valueOf(releaseStatus)
          }
          .toSet()
        val versions = unionedVersions
          .sortedWith(compareBy(artifact.versioningStrategy.comparator) { (version, _, _) -> version })
          .groupBy({ (_, _, promotionStatus) ->
            promotionStatus
          }, { (version, _, _) ->
            version
          })

        val currentVersion = versions[CURRENT]?.firstOrNull()
        ArtifactVersions(
          name = artifact.name,
          type = artifact.type,
          reference = artifact.reference,
          statuses = releaseStatuses,
          versions = ArtifactVersionStatus(
            current = currentVersion,
            deploying = versions[DEPLOYING]?.firstOrNull(),
            // take out stateful constraint values that will never happen
            pending = removeOlderIfCurrentExists(artifact, currentVersion, versions[PENDING]),
            approved = versions[APPROVED] ?: emptyList(),
            previous = versions[PREVIOUS] ?: emptyList(),
            vetoed = versions[VETOED] ?: emptyList(),
            skipped = removeNewerIfCurrentExists(artifact, currentVersion, versions[PENDING]).plus(versions[SKIPPED]
              ?: emptyList())
          ),
          pinnedVersion = pinnedEnvs.find { it.targetEnvironment == environment.name }?.version
        )
      }.toSet()
      EnvironmentSummary(environment, artifactVersions)
    }
  }

  override fun pinEnvironment(deliveryConfig: DeliveryConfig, environmentArtifactPin: EnvironmentArtifactPin) {
    with(environmentArtifactPin) {
      val environment = deliveryConfig.environmentNamed(targetEnvironment)
      val artifact = get(deliveryConfig.name, reference)

      sqlRetry.withRetry(WRITE) {
        jooq.insertInto(ENVIRONMENT_ARTIFACT_PIN)
          .set(ENVIRONMENT_ARTIFACT_PIN.ENVIRONMENT_UID, deliveryConfig.getUidFor(environment))
          .set(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_UID, artifact.uid)
          .set(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_VERSION, version)
          .set(ENVIRONMENT_ARTIFACT_PIN.PINNED_AT, clock.millis())
          .set(ENVIRONMENT_ARTIFACT_PIN.PINNED_BY, pinnedBy ?: "anonymous")
          .set(ENVIRONMENT_ARTIFACT_PIN.COMMENT, comment)
          .onDuplicateKeyUpdate()
          .set(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_VERSION, version)
          .set(ENVIRONMENT_ARTIFACT_PIN.PINNED_AT, clock.millis())
          .set(ENVIRONMENT_ARTIFACT_PIN.PINNED_BY, pinnedBy ?: "anonymous")
          .set(ENVIRONMENT_ARTIFACT_PIN.COMMENT, MySQLDSL.values(ENVIRONMENT_ARTIFACT_PIN.COMMENT))
          .execute()
      }
    }
  }

  override fun getPinnedEnvironments(deliveryConfig: DeliveryConfig): List<PinnedEnvironment> {
    return sqlRetry.withRetry(READ) {
      jooq.select(
        ENVIRONMENT.NAME,
        ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_VERSION,
        ENVIRONMENT_ARTIFACT_PIN.PINNED_AT,
        ENVIRONMENT_ARTIFACT_PIN.PINNED_BY,
        ENVIRONMENT_ARTIFACT_PIN.COMMENT,
        DELIVERY_ARTIFACT.NAME,
        DELIVERY_ARTIFACT.TYPE,
        DELIVERY_ARTIFACT.DETAILS,
        DELIVERY_ARTIFACT.REFERENCE
      )
        .from(ENVIRONMENT)
        .innerJoin(ENVIRONMENT_ARTIFACT_PIN)
        .on(ENVIRONMENT_ARTIFACT_PIN.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
        .innerJoin(DELIVERY_ARTIFACT)
        .on(DELIVERY_ARTIFACT.UID.eq(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_UID))
        .innerJoin(DELIVERY_CONFIG)
        .on(DELIVERY_CONFIG.UID.eq(ENVIRONMENT.DELIVERY_CONFIG_UID))
        .where(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
        .fetch { (environmentName, version, pinnedAt, pinnedBy, comment, artifactName, type, details, reference) ->
          PinnedEnvironment(
            deliveryConfigName = deliveryConfig.name,
            targetEnvironment = environmentName,
            artifact = mapToArtifact(
              artifactName,
              ArtifactType.valueOf(type.toLowerCase()),
              details,
              reference,
              deliveryConfig.name),
            version = version,
            pinnedAt = Instant.ofEpochMilli(pinnedAt),
            pinnedBy = pinnedBy,
            comment = comment
          )
        }
    }
  }

  override fun deletePin(deliveryConfig: DeliveryConfig, targetEnvironment: String) {
    sqlRetry.withRetry(WRITE) {
      jooq.select(ENVIRONMENT_ARTIFACT_PIN.ENVIRONMENT_UID, ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_UID)
        .from(ENVIRONMENT_ARTIFACT_PIN)
        .innerJoin(ENVIRONMENT)
        .on(ENVIRONMENT_ARTIFACT_PIN.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
        .where(
          ENVIRONMENT.NAME.eq(targetEnvironment),
          ENVIRONMENT.DELIVERY_CONFIG_UID.eq(deliveryConfig.uid))
        .fetch { (envUid, artUid) ->
          deletePin(envUid, artUid)
        }
    }
  }

  override fun deletePin(
    deliveryConfig: DeliveryConfig,
    targetEnvironment: String,
    reference: String
  ) {
    sqlRetry.withRetry(WRITE) {
      jooq.select(ENVIRONMENT_ARTIFACT_PIN.ENVIRONMENT_UID, ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_UID)
        .from(DELIVERY_ARTIFACT)
        .innerJoin(ENVIRONMENT_ARTIFACT_PIN)
        .on(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_UID.eq(DELIVERY_ARTIFACT.UID))
        .innerJoin(ENVIRONMENT)
        .on(ENVIRONMENT_ARTIFACT_PIN.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
        .where(
          DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfig.name),
          DELIVERY_ARTIFACT.REFERENCE.eq(reference),
          ENVIRONMENT.NAME.eq(targetEnvironment),
          ENVIRONMENT.DELIVERY_CONFIG_UID.eq(deliveryConfig.uid))
        .fetch { (envUid, artUid) ->
          deletePin(envUid, artUid)
        }
    }
  }

  override fun getArtifactSummaryInEnvironment(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifactReference: String,
    version: String
  ): ArtifactSummaryInEnvironment? {
    return sqlRetry.withRetry(READ) {

      val artifact = deliveryConfig.artifacts.firstOrNull { it.reference == artifactReference }
        ?: error("Artifact not found: name=$artifactReference, deliveryConfig=${deliveryConfig.name}")

      jooq
        .select(
          ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION,
          ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT,
          ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS,
          ENVIRONMENT_ARTIFACT_VERSIONS.REPLACED_BY,
          ENVIRONMENT_ARTIFACT_VERSIONS.REPLACED_AT
        )
        .from(ENVIRONMENT_ARTIFACT_VERSIONS)
        .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(deliveryConfig.getUidFor(environmentName)))
        .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifact.uid))
        .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.eq(version))
        .orderBy(ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT.desc())
        .fetchOne { (version, deployedAt, promotionStatus, replacedBy, replacedAt) ->
          val pinned: Pinned? = jooq
            .select(ENVIRONMENT_ARTIFACT_PIN.PINNED_BY, ENVIRONMENT_ARTIFACT_PIN.PINNED_AT, ENVIRONMENT_ARTIFACT_PIN.COMMENT)
            .from(ENVIRONMENT_ARTIFACT_PIN)
            .where(ENVIRONMENT_ARTIFACT_PIN.ENVIRONMENT_UID.eq(deliveryConfig.getUidFor(environmentName)))
            .and(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_UID.eq(artifact.uid))
            .and(ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_VERSION.eq(version))
            .fetchOne { (pinnedBy, pinnedAt, comment) ->
              Pinned(at = Instant.ofEpochMilli(pinnedAt), by = pinnedBy, comment = comment)
            }

          ArtifactSummaryInEnvironment(
            environment = environmentName,
            version = version,
            state = promotionStatus.toLowerCase(),
            deployedAt = deployedAt?.toInstant(ZoneOffset.UTC),
            replacedAt = replacedAt?.toInstant(ZoneOffset.UTC),
            replacedBy = replacedBy,
            isPinned = pinned != null,
            pinned = pinned
          )
        }
    }
  }

  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryArtifact> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck).toLocal()
    return sqlRetry.withRetry(WRITE) {
      jooq.inTransaction {
        select(DELIVERY_ARTIFACT.UID, DELIVERY_ARTIFACT.NAME, DELIVERY_ARTIFACT.TYPE, DELIVERY_ARTIFACT.DETAILS, DELIVERY_ARTIFACT.REFERENCE, DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME)
          .from(DELIVERY_ARTIFACT, ARTIFACT_LAST_CHECKED)
          .where(DELIVERY_ARTIFACT.UID.eq(ARTIFACT_LAST_CHECKED.ARTIFACT_UID))
          .and(ARTIFACT_LAST_CHECKED.AT.lessOrEqual(cutoff))
          .orderBy(ARTIFACT_LAST_CHECKED.AT)
          .limit(limit)
          .forUpdate()
          .fetch()
          .also {
            it.forEach { (uid, _, _, _, _, _) ->
              insertInto(ARTIFACT_LAST_CHECKED)
                .set(ARTIFACT_LAST_CHECKED.ARTIFACT_UID, uid)
                .set(ARTIFACT_LAST_CHECKED.AT, now.toLocal())
                .onDuplicateKeyUpdate()
                .set(ARTIFACT_LAST_CHECKED.AT, now.toLocal())
                .execute()
            }
          }
          .map { (_, name, type, details, reference, deliveryConfigName) ->
            mapToArtifact(name, ArtifactType.valueOf(type), details, reference, deliveryConfigName)
          }
      }
    }
  }

  private fun priorVersionDeployedIn(
    environmentId: String,
    artifactId: String,
    currentVersion: String
  ): String? {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION)
        .from(ENVIRONMENT_ARTIFACT_VERSIONS)
        .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(environmentId))
        .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_UID.eq(artifactId))
        .and(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION.ne(currentVersion))
        .and(ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT.isNotNull)
        .and(ENVIRONMENT_ARTIFACT_VERSIONS.PROMOTION_STATUS.ne(VETOED.name))
        .orderBy(ENVIRONMENT_ARTIFACT_VERSIONS.DEPLOYED_AT.desc())
        .limit(1)
        .fetchOne(ENVIRONMENT_ARTIFACT_VERSIONS.ARTIFACT_VERSION)
    }
  }

  private fun deletePin(envUid: String, artUid: String) {
    // Deletes rows by primary key
    jooq.deleteFrom(ENVIRONMENT_ARTIFACT_PIN)
      .where(
        ENVIRONMENT_ARTIFACT_PIN.ENVIRONMENT_UID.eq(envUid),
        ENVIRONMENT_ARTIFACT_PIN.ARTIFACT_UID.eq(artUid)
      )
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

  private fun DeliveryConfig.getUidFor(environmentName: String): Select<Record1<String>> =
    select(ENVIRONMENT.UID)
      .from(ENVIRONMENT)
      .where(ENVIRONMENT.NAME.eq(environmentName))
      .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(uid))

  private fun DeliveryConfig.getUidStringFor(environment: Environment): String =
    jooq.select(ENVIRONMENT.UID)
      .from(ENVIRONMENT)
      .where(ENVIRONMENT.NAME.eq(environment.name))
      .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(uid))
      .fetchOne(ENVIRONMENT.UID) ?: error("environment not found for $name / ${environment.name}")

  private val DeliveryArtifact.uid: Select<Record1<String>>
    get() = select(DELIVERY_ARTIFACT.UID)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(name)
        .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
        .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName))
        .and(DELIVERY_ARTIFACT.REFERENCE.eq(reference)))

  private val DeliveryArtifact.uidString: String
    get() = sqlRetry.withRetry(READ) {
      jooq.select(DELIVERY_ARTIFACT.UID)
        .from(DELIVERY_ARTIFACT)
        .where(DELIVERY_ARTIFACT.NAME.eq(name)
          .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
          .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName))
          .and(DELIVERY_ARTIFACT.REFERENCE.eq(reference)))
        .fetchOne(DELIVERY_ARTIFACT.UID) ?: error("artifact not found for " +
        "name=$name, " +
        "type=$type, " +
        "deliveryConfig=$deliveryConfigName, " +
        "reference=$reference")
    }

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

  private fun vetoKey(envName: String, artifactId: String) = "$envName:$artifactId"

  private fun Instant.toLocal() = atZone(clock.zone).toLocalDateTime()

  private fun currentTimestamp() = clock.instant().toLocal()

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
