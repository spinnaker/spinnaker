package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.allPass
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.statefulCount
import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.core.api.UID
import com.netflix.spinnaker.keel.core.api.parseUID
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.core.api.timestampAsInstant
import com.netflix.spinnaker.keel.events.ResourceState
import com.netflix.spinnaker.keel.pause.PauseScope
import com.netflix.spinnaker.keel.pause.PauseScope.APPLICATION
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter.ATTACH_NONE
import com.netflix.spinnaker.keel.persistence.NoDeliveryConfigForApplication
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.persistence.OrphanedResourceException
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTIVE_ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTIVE_ENVIRONMENT_VERSION
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTIVE_RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.CURRENT_CONSTRAINT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG_LAST_CHECKED
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_ARTIFACT_CONSTRAINT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_VERSION
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_VERSION_ARTIFACT_VERSION
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.EVENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.PAUSED
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.PREVIEW_ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE_LAST_CHECKED
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE_VERSION
import com.netflix.spinnaker.keel.resources.ResourceFactory
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import com.netflix.spinnaker.keel.sql.deliveryconfigs.attachDependents
import com.netflix.spinnaker.keel.sql.deliveryconfigs.deliveryConfigByName
import com.netflix.spinnaker.keel.sql.deliveryconfigs.makeEnvironment
import com.netflix.spinnaker.keel.sql.deliveryconfigs.selectEnvironmentColumns
import com.netflix.spinnaker.keel.sql.deliveryconfigs.uid
import com.netflix.spinnaker.keel.telemetry.AboutToBeChecked
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL
import org.jooq.impl.DSL.coalesce
import org.jooq.impl.DSL.count
import org.jooq.impl.DSL.inline
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.selectOne
import org.jooq.impl.DSL.value
import org.jooq.util.mysql.MySQLDSL.values
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH

class SqlDeliveryConfigRepository(
  jooq: DSLContext,
  clock: Clock,
  objectMapper: ObjectMapper,
  resourceFactory: ResourceFactory,
  sqlRetry: SqlRetry,
  artifactSuppliers: List<ArtifactSupplier<*, *>> = emptyList(),
  private val publisher: ApplicationEventPublisher
) : SqlStorageContext(
  jooq,
  clock,
  sqlRetry,
  objectMapper,
  resourceFactory,
  artifactSuppliers
), DeliveryConfigRepository {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val RECHECK_LEASE_NAME = "recheck"

  override fun isApplicationConfigured(application: String): Boolean =
    sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_CONFIG.APPLICATION)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.APPLICATION.eq(application))
        .fetchOne()
    } != null


  override fun getByApplication(application: String): DeliveryConfig =
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          DELIVERY_CONFIG.UID,
          DELIVERY_CONFIG.NAME,
          DELIVERY_CONFIG.APPLICATION,
          DELIVERY_CONFIG.SERVICE_ACCOUNT,
          DELIVERY_CONFIG.METADATA,
          DELIVERY_CONFIG.RAW_CONFIG,
          DELIVERY_CONFIG.UPDATED_AT,
        )
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.APPLICATION.eq(application))
        .fetchOne { (uid, name, application, serviceAccount, metadata, rawConfig, updatedAt) ->
          DeliveryConfig(
            name = name,
            application = application,
            serviceAccount = serviceAccount,
            metadata = (metadata ?: emptyMap()) + mapOf("createdAt" to ULID.parseULID(uid).timestampAsInstant()),
            rawConfig = rawConfig,
            updatedAt = updatedAt
          ).let {
            attachDependents(it)
          }
        }
    } ?: throw NoDeliveryConfigForApplication(application)

  override fun deleteResourceFromEnv(
    deliveryConfigName: String,
    environmentName: String,
    resourceId: String
  ) {
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(ENVIRONMENT_RESOURCE)
        .where(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(envUid(deliveryConfigName, environmentName)))
        .and(ENVIRONMENT_RESOURCE.RESOURCE_UID.`in`(resourceId.uids))
        .execute()
    }
  }

  override fun deleteEnvironment(deliveryConfigName: String, environmentName: String) {
    val deliveryConfigUid = deliveryConfigUidByName(deliveryConfigName)

    environmentUidByName(deliveryConfigName, environmentName)
      ?.let { envUid ->
        sqlRetry.withRetry(WRITE) {
          jooq.transaction { config ->
            val txn = DSL.using(config)
            txn
              .deleteFrom(ENVIRONMENT)
              .where(ENVIRONMENT.UID.eq(envUid))
              .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(deliveryConfigUid))
              .execute()
          }
        }
      }
  }

  override fun deletePreviewEnvironment(deliveryConfigName: String, baseEnvironmentName: String) {
    val deliveryConfigUid = deliveryConfigUidByName(deliveryConfigName)

    environmentUidByName(deliveryConfigName, baseEnvironmentName)?.let { baseEnvironmentUid ->
      sqlRetry.withRetry(WRITE) {
        jooq
          .deleteFrom(PREVIEW_ENVIRONMENT)
          .where(PREVIEW_ENVIRONMENT.DELIVERY_CONFIG_UID.eq(deliveryConfigUid))
          .and(PREVIEW_ENVIRONMENT.BASE_ENVIRONMENT_UID.eq(baseEnvironmentUid))
          .execute()
      }
    }
  }

  override fun deleteByName(name: String) {
    val application = sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_CONFIG.APPLICATION)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.NAME.eq(name))
        .fetchOne(DELIVERY_CONFIG.APPLICATION)
    } ?: throw NoSuchDeliveryConfigName(name)
    deleteByApplication(application)
  }

  override fun deleteByApplication(application: String) {
    val configUid = getUIDByApplication(application)
    val resourceUids = getResourceUIDs(application)
    val resourceIds = getResourceIDs(application)
    val artifactUids = getArtifactUIDs(configUid)

    sqlRetry.withRetry(WRITE) {
      jooq.transaction { config ->
        val txn = DSL.using(config)
        // delete events
        txn.deleteFrom(EVENT)
          .where(EVENT.APPLICATION.eq(application))
          .execute()
        // delete pause records
        txn.deleteFrom(PAUSED)
          .where(PAUSED.SCOPE.eq(PauseScope.RESOURCE))
          .and(PAUSED.NAME.`in`(resourceIds))
          .execute()
        txn.deleteFrom(PAUSED)
          .where(PAUSED.SCOPE.eq(PauseScope.APPLICATION))
          .and(PAUSED.NAME.eq(application))
          .execute()
        // delete resources
        txn.deleteFrom(RESOURCE)
          .where(RESOURCE.UID.`in`(resourceUids))
          .execute()
        // delete artifact
        txn.deleteFrom(DELIVERY_ARTIFACT)
          .where(DELIVERY_ARTIFACT.UID.`in`(artifactUids))
          .execute()
        // delete delivery config
        txn.deleteFrom(DELIVERY_CONFIG)
          .where(DELIVERY_CONFIG.UID.eq(configUid))
          .execute()
      }
    }
  }

  private fun getUIDByApplication(application: String): String =
    sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_CONFIG.UID)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.APPLICATION.eq(application))
        .fetchOne(DELIVERY_CONFIG.UID)
    } ?: throw NoDeliveryConfigForApplication(application)

  /**
   * This deliberately returns _all_ resource UIDs not just the latest versions as it is used when
   * deleting the resources associated with a delivery config.
   */
  private fun getResourceUIDs(application: String): Select<Record1<String>> =
    jooq
      .select(RESOURCE.UID)
      .from(RESOURCE)
      .where(RESOURCE.APPLICATION.eq(application))

  private fun getResourceIDs(application: String): Select<Record1<String>> =
    jooq
      .selectDistinct(RESOURCE.ID)
      .from(RESOURCE)
      .where(RESOURCE.APPLICATION.eq(application))

  private fun getArtifactUIDs(deliveryConfigUid: String): Select<Record1<String>> =
    jooq
      .select(DELIVERY_CONFIG_ARTIFACT.ARTIFACT_UID)
      .from(DELIVERY_CONFIG_ARTIFACT)
      .where(DELIVERY_CONFIG_ARTIFACT.DELIVERY_CONFIG_UID.eq(deliveryConfigUid))

  // todo: queries in this function aren't inherently retryable because of the cross-repository interactions
  // from where this is called: https://github.com/spinnaker/keel/issues/740
  override fun store(deliveryConfig: DeliveryConfig) {
    with(deliveryConfig) {
      val deliveryConfigUid = deliveryConfigUidByName(name)
        ?: randomUID().toString()

      jooq.insertInto(DELIVERY_CONFIG)
        .set(DELIVERY_CONFIG.UID, deliveryConfigUid)
        .set(DELIVERY_CONFIG.NAME, name)
        .set(DELIVERY_CONFIG.APPLICATION, application)
        .set(DELIVERY_CONFIG.SERVICE_ACCOUNT, serviceAccount)
        .set(DELIVERY_CONFIG.METADATA, metadata)
        .set(DELIVERY_CONFIG.RAW_CONFIG, rawConfig)
        .set(DELIVERY_CONFIG.UPDATED_AT, clock.instant())
        .onDuplicateKeyUpdate()
        .set(DELIVERY_CONFIG.SERVICE_ACCOUNT, serviceAccount)
        .set(DELIVERY_CONFIG.METADATA, metadata)
        .set(DELIVERY_CONFIG.RAW_CONFIG, rawConfig)
        .set(DELIVERY_CONFIG.UPDATED_AT, clock.instant())
        .execute()

      artifacts.forEach { artifact ->
        jooq.insertInto(DELIVERY_CONFIG_ARTIFACT)
          .set(DELIVERY_CONFIG_ARTIFACT.DELIVERY_CONFIG_UID, deliveryConfigUid)
          .set(
            DELIVERY_CONFIG_ARTIFACT.ARTIFACT_UID,
            jooq
              .select(DELIVERY_ARTIFACT.UID)
              .from(DELIVERY_ARTIFACT)
              .where(DELIVERY_ARTIFACT.NAME.eq(artifact.name))
              .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type))
              .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(artifact.deliveryConfigName))
              .and(DELIVERY_ARTIFACT.REFERENCE.eq(artifact.reference))
          )
          .onDuplicateKeyIgnore()
          .execute()
      }

      environments.forEach { environment ->
        storeEnvironment(this, environment)
      }

      previewEnvironments.forEach { previewEnvSpec ->
        environmentUidByName(name, previewEnvSpec.baseEnvironment)?.let { baseEnvironmentUid ->
          jooq.insertInto(PREVIEW_ENVIRONMENT)
            .set(PREVIEW_ENVIRONMENT.DELIVERY_CONFIG_UID, deliveryConfigUid)
            .set(PREVIEW_ENVIRONMENT.BASE_ENVIRONMENT_UID, baseEnvironmentUid)
            .set(PREVIEW_ENVIRONMENT.BRANCH_FILTER, previewEnvSpec.branch.toJson())
            .set(PREVIEW_ENVIRONMENT.VERIFICATIONS, previewEnvSpec.verifyWith.toJson())
            .set(PREVIEW_ENVIRONMENT.NOTIFICATIONS, previewEnvSpec.notifications.toJson())
            .onDuplicateKeyUpdate()
            .set(PREVIEW_ENVIRONMENT.BRANCH_FILTER, previewEnvSpec.branch.toJson())
            .set(PREVIEW_ENVIRONMENT.VERIFICATIONS, previewEnvSpec.verifyWith.toJson())
            .set(PREVIEW_ENVIRONMENT.NOTIFICATIONS, previewEnvSpec.notifications.toJson())
            .execute()
        }
      }

      jooq.insertInto(DELIVERY_CONFIG_LAST_CHECKED)
        .set(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID, deliveryConfigUid)
        .set(DELIVERY_CONFIG_LAST_CHECKED.AT, EPOCH.plusSeconds(1))
        .onDuplicateKeyUpdate()
        .set(DELIVERY_CONFIG_LAST_CHECKED.AT, EPOCH.plusSeconds(1))
        .execute()
    }
  }

  private fun storeEnvironment(deliveryConfig: DeliveryConfig, environment: Environment) {
    val environmentUid = (
      jooq
        .select(ENVIRONMENT.UID)
        .from(ENVIRONMENT)
        .where(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(deliveryConfig.uid))
        .and(ENVIRONMENT.NAME.eq(environment.name))
        .fetchOne(ENVIRONMENT.UID)
        ?: randomUID().toString()
      )

    // Add some default metadata that's useful in a bunch of places
    environment.addMetadata(
      "uid" to environmentUid,
      "application" to deliveryConfig.application,
      "deliveryConfigName" to deliveryConfig.name
    )

    jooq.insertInto(ENVIRONMENT)
      .set(ENVIRONMENT.UID, environmentUid)
      .set(ENVIRONMENT.DELIVERY_CONFIG_UID, deliveryConfig.uid)
      .set(ENVIRONMENT.NAME, environment.name)
      .set(ENVIRONMENT.IS_PREVIEW, environment.isPreview)
      .set(ENVIRONMENT.CONSTRAINTS, environment.constraints.toJson())
      .set(ENVIRONMENT.NOTIFICATIONS, environment.notifications.toJson())
      .set(ENVIRONMENT.VERIFICATIONS, environment.verifyWith.toJson())
      .set(ENVIRONMENT.POST_DEPLOY_ACTIONS, environment.postDeploy.toJson())
      .set(ENVIRONMENT.METADATA, environment.metadata.toJson())
      .onDuplicateKeyUpdate()
      .set(ENVIRONMENT.CONSTRAINTS, environment.constraints.toJson())
      .set(ENVIRONMENT.NOTIFICATIONS, environment.notifications.toJson())
      .set(ENVIRONMENT.VERIFICATIONS, environment.verifyWith.toJson())
      .set(ENVIRONMENT.POST_DEPLOY_ACTIONS, environment.postDeploy.toJson())
      .set(ENVIRONMENT.METADATA, environment.metadata.toJson())
      .execute()
    val currentVersion = jooq
      .select(coalesce(max(ENVIRONMENT_VERSION.VERSION), value(0)))
      .from(ENVIRONMENT_VERSION)
      .where(ENVIRONMENT_VERSION.ENVIRONMENT_UID.eq(environmentUid))
      .fetchSingleInto<Int>()

    val newVersion = currentVersion + 1

    val currentVersionResources = resourceUidsAndVersionsFor(environmentUid, currentVersion)
    val newVersionResources = environment.latestResourceUidsAndVersions()

    val newVersionRequired = if (currentVersion == 0) {
      log.debug("Creating initial version of environment {}/{}", deliveryConfig.application, environment.name)
      true
    } else if (currentVersionResources != newVersionResources) {
      log.debug(
        "Creating a new version {} of environment {}/{} because resources changed from {} to {}",
        newVersion,
        deliveryConfig.application,
        environment.name,
        currentVersionResources,
        newVersionResources
      )
      true
    } else {
      false
    }

    if (newVersionRequired) {
      jooq.insertInto(ENVIRONMENT_VERSION)
        .set(ENVIRONMENT_VERSION.ENVIRONMENT_UID, environmentUid)
        .set(ENVIRONMENT_VERSION.VERSION, newVersion)
        .set(ENVIRONMENT_VERSION.CREATED_AT, clock.instant())
        .execute()

      // make the new environment version 'active'
      jooq.setActiveEnvironmentVersion(environmentUid, newVersion)

      jooq.insertInto(ENVIRONMENT_VERSION_ARTIFACT_VERSION)
        .columns(
          ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_UID,
          ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_VERSION,
          ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_UID,
          ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_VERSION
        )
        .select(
          select(
            ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_UID,
            value(newVersion),
            ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_UID,
            ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_VERSION
          )
            .from(ENVIRONMENT_VERSION_ARTIFACT_VERSION)
            .where(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_UID.eq(environmentUid))
            .and(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_VERSION.eq(currentVersion))
        )
        .execute()
    }

    newVersionResources.forEach { (resourceUid, resourceVersion) ->
      jooq.insertInto(ENVIRONMENT_RESOURCE)
        .set(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID, environmentUid)
        .set(ENVIRONMENT_RESOURCE.ENVIRONMENT_VERSION, newVersion)
        .set(ENVIRONMENT_RESOURCE.RESOURCE_UID, resourceUid)
        .set(ENVIRONMENT_RESOURCE.RESOURCE_VERSION, resourceVersion)
        .onDuplicateKeyIgnore()
        .execute()
    }
  }

  override fun storeEnvironment(deliveryConfigName: String, environment: Environment) {
    val deliveryConfig = deliveryConfigByName(deliveryConfigName, ATTACH_NONE)
    storeEnvironment(deliveryConfig, environment)
  }

  /**
   * @return a map of uid to version for the latest versions of all resources used in this
   * environment.
   */
  private fun Environment.latestResourceUidsAndVersions() =
    jooq.select(RESOURCE.UID, max(RESOURCE_VERSION.VERSION))
      .from(RESOURCE)
      .join(RESOURCE_VERSION)
      .on(RESOURCE.UID.eq(RESOURCE_VERSION.RESOURCE_UID))
      .where(RESOURCE.ID.`in`(resources.map(Resource<*>::id)))
      .groupBy(RESOURCE.UID)
      .fetch { (uid, version) -> uid to version }
      .toMap()

  /**
   * @return a map of uid to version for all resources used in [version] of an environment.
   */
  private fun resourceUidsAndVersionsFor(environmentUid: String, version: Int) =
    when (version) {
      0 -> emptyMap()
      else -> jooq
        .select(RESOURCE.UID, RESOURCE_VERSION.VERSION)
        .from(RESOURCE)
        .join(RESOURCE_VERSION)
        .on(RESOURCE.UID.eq(RESOURCE_VERSION.RESOURCE_UID))
        .whereExists(
          selectOne()
            .from(ENVIRONMENT_RESOURCE)
            .where(ENVIRONMENT_RESOURCE.RESOURCE_UID.eq(RESOURCE.UID))
            .and(ENVIRONMENT_RESOURCE.RESOURCE_VERSION.eq(RESOURCE_VERSION.VERSION))
            .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(environmentUid))
            .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_VERSION.eq(version))
        )
        .fetch { (uid, version) -> uid to version }
        .toMap()
    }

  override fun get(name: String): DeliveryConfig =
    deliveryConfigByName(name)

  override fun all(vararg dependentAttachFilter: DependentAttachFilter): Set<DeliveryConfig> =
    sqlRetry.withRetry(READ) {
      jooq.select(
        DELIVERY_CONFIG.UID,
        DELIVERY_CONFIG.NAME,
        DELIVERY_CONFIG.APPLICATION,
        DELIVERY_CONFIG.SERVICE_ACCOUNT,
        DELIVERY_CONFIG.METADATA
      )
        .from(DELIVERY_CONFIG)
        .fetch { (uid, name, application, serviceAccount, metadata) ->
          DeliveryConfig(
            name = name,
            application = application,
            serviceAccount = serviceAccount,
            metadata = (metadata ?: emptyMap()) + mapOf("createdAt" to ULID.parseULID(uid).timestampAsInstant())
          )
        }
        .map { deliveryConfig ->
          attachDependents(deliveryConfig, *dependentAttachFilter)
        }.toSet()
    }

  override fun environmentFor(resourceId: String): Environment =
    sqlRetry.withRetry(READ) {
      jooq
        .selectEnvironmentColumns()
        .from(ACTIVE_ENVIRONMENT, ENVIRONMENT_RESOURCE, RESOURCE)
        .where(RESOURCE.ID.eq(resourceId))
        .and(ENVIRONMENT_RESOURCE.RESOURCE_UID.eq(RESOURCE.UID))
        .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(ACTIVE_ENVIRONMENT.UID))
        .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_VERSION.eq(ACTIVE_ENVIRONMENT.VERSION))
        .fetchOne { record ->
          makeEnvironment(record, objectMapper)
        }
    } ?: throw OrphanedResourceException(resourceId)

  override fun environmentNotifications(deliveryConfigName: String, environmentName: String): Set<NotificationConfig> =
    sqlRetry.withRetry(READ) {
      val uid = jooq
        .select(DELIVERY_CONFIG.UID)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.NAME.eq(deliveryConfigName))
        .fetchOne(DELIVERY_CONFIG.UID)
        ?: randomUID().toString()
      jooq
        .select(ENVIRONMENT.NOTIFICATIONS)
        .from(ENVIRONMENT)
        .where(ENVIRONMENT.NAME.eq(environmentName))
        .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(uid))
        .fetchOne { (notificationsJson) ->
          notificationsJson?.let { objectMapper.readValue(it) }
        } ?: emptySet()
    }

  override fun resourceStatusesInEnvironment(
    deliveryConfigName: String,
    environmentName: String
  ): Map<String, ResourceState> =
    jooq
      .select(RESOURCE.ID, RESOURCE_LAST_CHECKED.STATUS)
      .from(RESOURCE)
      .join(RESOURCE_LAST_CHECKED)
      .on(RESOURCE_LAST_CHECKED.RESOURCE_UID.eq(RESOURCE.UID))
      .join(ENVIRONMENT_RESOURCE)
      .on(ENVIRONMENT_RESOURCE.RESOURCE_UID.eq(RESOURCE.UID))
      .join(ACTIVE_ENVIRONMENT)
      .on(ACTIVE_ENVIRONMENT.UID.eq(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID))
      .and(ACTIVE_ENVIRONMENT.VERSION.eq(ENVIRONMENT_RESOURCE.ENVIRONMENT_VERSION))
      .and(ACTIVE_ENVIRONMENT.NAME.eq(environmentName))
      .join(DELIVERY_CONFIG)
      .on(DELIVERY_CONFIG.UID.eq(ACTIVE_ENVIRONMENT.DELIVERY_CONFIG_UID))
      .and(DELIVERY_CONFIG.NAME.eq(deliveryConfigName))
      .fetchMap(RESOURCE.ID, RESOURCE_LAST_CHECKED.STATUS)

  override fun deliveryConfigFor(resourceId: String): DeliveryConfig =
    // TODO: this implementation could be more efficient by sharing code with get(name)
    sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_CONFIG.NAME)
        .from(ACTIVE_ENVIRONMENT, ENVIRONMENT_RESOURCE, RESOURCE, DELIVERY_CONFIG)
        .where(RESOURCE.ID.eq(resourceId))
        .and(ENVIRONMENT_RESOURCE.RESOURCE_UID.eq(RESOURCE.UID))
        .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(ACTIVE_ENVIRONMENT.UID))
        .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_VERSION.eq(ACTIVE_ENVIRONMENT.VERSION))
        .and(ACTIVE_ENVIRONMENT.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))
        .fetchOne { (name) ->
          get(name)
        }
    } ?: throw OrphanedResourceException(resourceId)

  /**
   * gets or creates constraint id
   * saves constraint
   * calls [constraintStateForWithTransaction]
   * if all constraints pass, puts in queue for approval table
   */
  override fun storeConstraintState(state: ConstraintState) {
    environmentUidByName(state.deliveryConfigName, state.environmentName)
      ?.also { envUid ->
        val uid = sqlRetry.withRetry(READ) {
          jooq
            .select(ENVIRONMENT_ARTIFACT_CONSTRAINT.UID)
            .from(ENVIRONMENT_ARTIFACT_CONSTRAINT)
            .where(
              ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID.eq(envUid),
              ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE.eq(state.type),
              ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION.eq(state.artifactVersion),
              ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE.eq(state.artifactReference)
            )
            .fetchOne(ENVIRONMENT_ARTIFACT_CONSTRAINT.UID)
        } ?: randomUID().toString()

        val application = applicationByDeliveryConfigName(state.deliveryConfigName)
        val environment = get(state.deliveryConfigName)
          .environments
          .firstOrNull {
            it.name == state.environmentName
          }
          ?: error("Environment ${state.environmentName} does not exist in ${state.deliveryConfigName}")

        sqlRetry.withRetry(WRITE) {
          jooq.transaction { config ->
            val txn = DSL.using(config)
            txn
              .insertInto(ENVIRONMENT_ARTIFACT_CONSTRAINT)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.UID, uid)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID, envUid)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION, state.artifactVersion)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE, state.artifactReference)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE, state.type)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT, state.createdAt)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS, state.status)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY, state.judgedBy)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT, state.judgedAt)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT, state.comment)
              .set(
                ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES,
                objectMapper.writeValueAsString(state.attributes)
              )
              .onDuplicateKeyUpdate()
              .set(
                ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS,
                values(ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS)
              )
              .set(
                ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY,
                values(ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY)
              )
              .set(
                ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT,
                values(ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT)
              )
              .set(
                ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT,
                values(ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT)
              )
              .set(
                ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES,
                values(ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES)
              )
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE, state.artifactReference)
              .execute()

            txn
              .insertInto(CURRENT_CONSTRAINT)
              .set(CURRENT_CONSTRAINT.APPLICATION, application)
              .set(CURRENT_CONSTRAINT.ENVIRONMENT_UID, envUid)
              .set(CURRENT_CONSTRAINT.TYPE, state.type)
              .set(CURRENT_CONSTRAINT.CONSTRAINT_UID, uid)
              .onDuplicateKeyUpdate()
              .set(
                CURRENT_CONSTRAINT.CONSTRAINT_UID,
                values(CURRENT_CONSTRAINT.CONSTRAINT_UID)
              )
              .execute()

            /**
             * Passing the transaction here since [constraintStateForWithTransaction] is querying [ENVIRONMENT_ARTIFACT_CONSTRAINT]
             * table, and we need to make sure the new state was persisted prior to checking all states for a given artifact version.
             *
             * We need to do this so that stateful constraints that aren't the latest still get approved for deployment.
             */
            val allStates = constraintStateForWithTransaction(
              state.deliveryConfigName,
              state.environmentName,
              state.artifactVersion,
              state.artifactReference,
              txn
            )
            if (allStates.allPass && allStates.size >= environment.constraints.statefulCount) {
              txn.insertInto(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL)
                .set(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ENVIRONMENT_UID, envUid)
                .set(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ARTIFACT_VERSION, state.artifactVersion)
                .set(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.QUEUED_AT, clock.instant())
                .set(
                  ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ARTIFACT_REFERENCE,
                  state.artifactReference
                )
                .onDuplicateKeyIgnore()
                .execute()
            }
          }
          // Store generated UID in constraint state object so it can be used by caller
          state.uid = parseUID(uid)
        }
      }
  }

  override fun getConstraintState(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String,
    type: String,
    artifactReference: String?
  ): ConstraintState? {
    val environmentUID = environmentUidByName(deliveryConfigName, environmentName)
      ?: return null
    return sqlRetry.withRetry(READ) {
      jooq
        .select(
          inline(deliveryConfigName).`as`("deliveryConfigName"),
          inline(environmentName).`as`("environmentName"),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES
        )
        .from(ENVIRONMENT_ARTIFACT_CONSTRAINT)
        .where(
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID.eq(environmentUID),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION.eq(artifactVersion),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE.eq(type),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE.eq(artifactReference)
        )
        .fetchOne { (
                      deliveryConfigName,
                      environmentName,
                      artifactVersion,
                      artifactReference,
                      constraintType,
                      status,
                      createdAt,
                      judgedBy,
                      judgedAt,
                      comment,
                      attributes
                    ) ->
          ConstraintState(
            deliveryConfigName,
            environmentName,
            artifactVersion,
            artifactReference,
            constraintType,
            status,
            createdAt,
            judgedBy,
            judgedAt,
            comment,
            objectMapper.readValue(attributes)
          )
        }
    }
  }

  override fun getConstraintStateById(uid: UID): ConstraintState? {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(
          DELIVERY_CONFIG.NAME,
          ENVIRONMENT.NAME,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES
        )
        .from(ENVIRONMENT_ARTIFACT_CONSTRAINT, DELIVERY_CONFIG, ENVIRONMENT)
        .where(ENVIRONMENT_ARTIFACT_CONSTRAINT.UID.eq(uid.toString()))
        .and(ENVIRONMENT.UID.eq(ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID))
        .and(DELIVERY_CONFIG.UID.eq(ENVIRONMENT.DELIVERY_CONFIG_UID))
        .fetchOne { (
                      deliveryConfigName,
                      environmentName,
                      artifactVersion,
                      artifactReference,
                      constraintType,
                      status,
                      createdAt,
                      judgedBy,
                      judgedAt,
                      comment,
                      attributes
                    ) ->
          ConstraintState(
            deliveryConfigName,
            environmentName,
            artifactVersion,
            artifactReference,
            constraintType,
            status,
            createdAt,
            judgedBy,
            judgedAt,
            comment,
            objectMapper.readValue(attributes)
          )
        }
    }
  }

  override fun deleteConstraintState(
    deliveryConfigName: String,
    environmentName: String,
    reference: String,
    version: String,
    type: String
  ): Int {
    val envUidSelect = envUid(deliveryConfigName, environmentName)
    return sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(ENVIRONMENT_ARTIFACT_CONSTRAINT)
        .where(
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID.eq(envUidSelect),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE.eq(type),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION.eq(version),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE.eq(reference),
        )
        .execute()
    }
  }

  override fun constraintStateFor(application: String): List<ConstraintState> {
    val environmentNames = mutableMapOf<String, String>()
    val deliveryConfigsByEnv = mutableMapOf<String, String>()
    val constraintResult = sqlRetry.withRetry(READ) {
      jooq
        .select(
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES
        )
        .from(CURRENT_CONSTRAINT)
        .innerJoin(ENVIRONMENT_ARTIFACT_CONSTRAINT)
        .on(CURRENT_CONSTRAINT.CONSTRAINT_UID.eq(ENVIRONMENT_ARTIFACT_CONSTRAINT.UID))
        .where(CURRENT_CONSTRAINT.APPLICATION.eq(application))
        .fetch()
    }

    if (constraintResult.isEmpty()) {
      return emptyList()
    }

    sqlRetry.withRetry(READ) {
      jooq
        .select(
          CURRENT_CONSTRAINT.ENVIRONMENT_UID,
          ENVIRONMENT.NAME,
          DELIVERY_CONFIG.NAME
        )
        .from(CURRENT_CONSTRAINT)
        .innerJoin(ENVIRONMENT)
        .on(ENVIRONMENT.UID.eq(CURRENT_CONSTRAINT.ENVIRONMENT_UID))
        .innerJoin(DELIVERY_CONFIG)
        .on(DELIVERY_CONFIG.UID.eq(ENVIRONMENT.DELIVERY_CONFIG_UID))
        .where(CURRENT_CONSTRAINT.APPLICATION.eq(application))
        .fetch { (envId, envName, dcName) ->
          environmentNames[envId] = envName
          deliveryConfigsByEnv[envId] = dcName
        }
    }

    return constraintResult.mapNotNull { (
                                           envId,
                                           artifactVersion,
                                           artifactReference,
                                           type,
                                           createdAt,
                                           status,
                                           judgedBy,
                                           judgedAt,
                                           comment,
                                           attributes
                                         ) ->
      if (deliveryConfigsByEnv.containsKey(envId) && environmentNames.containsKey(envId)) {
        ConstraintState(
          deliveryConfigsByEnv[envId]
            ?: error("Environment id $envId does not belong to a delivery-config"),
          environmentNames[envId] ?: error("Invalid environment id $envId"),
          artifactVersion,
          artifactReference,
          type,
          status,
          createdAt,
          judgedBy,
          judgedAt,
          comment,
          objectMapper.readValue(attributes)
        )
      } else {
        log.warn(
          "constraint state for " +
            "envId=$envId, " +
            "artifactVersion=$artifactVersion, " +
            "type=$type, " +
            " does not belong to a valid environment."
        )
        null
      }
    }
  }

  override fun constraintStateForEnvironments(deliveryConfigName: String, environmentUIDs: List<String>, limit: Int?): List<ConstraintState> {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(
          inline(deliveryConfigName).`as`("deliveryConfigName"),
          ENVIRONMENT.NAME.`as`("environmentName"),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES
        )
        .from(ENVIRONMENT_ARTIFACT_CONSTRAINT)
        .join(ENVIRONMENT)
        .on(ENVIRONMENT.UID.eq(ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID))
        .run {
          if (environmentUIDs.isNotEmpty()) {
            and(ENVIRONMENT.UID.`in`(environmentUIDs))
          } else {
            join(DELIVERY_CONFIG)
              .on(DELIVERY_CONFIG.UID.eq(ENVIRONMENT.DELIVERY_CONFIG_UID))
              .and(DELIVERY_CONFIG.NAME.eq(deliveryConfigName))
          }
        }
        .orderBy(ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT.desc())
        .apply {
          if (limit != null) {
            limit(limit)
          }
        }
        .fetch { (
                   deliveryConfigName,
                   environmentName,
                   artifactVersion,
                   artifactReference,
                   constraintType,
                   status,
                   createdAt,
                   judgedBy,
                   judgedAt,
                   comment,
                   attributes
                 ) ->
          ConstraintState(
            deliveryConfigName,
            environmentName,
            artifactVersion,
            artifactReference,
            constraintType,
            status,
            createdAt,
            judgedBy,
            judgedAt,
            comment,
            objectMapper.readValue(attributes)
          )
        }
    }
  }

  override fun constraintStateFor(
    deliveryConfigName: String,
    environmentName: String,
    limit: Int
  ): List<ConstraintState> {
    val environmentUID = environmentUidByName(deliveryConfigName, environmentName)
      ?: return emptyList()
    return constraintStateForEnvironments(deliveryConfigName, listOf(environmentUID), limit)
  }

  override fun constraintStateFor(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String,
    artifactReference: String
  ): List<ConstraintState> {
    return constraintStateForWithTransaction(deliveryConfigName, environmentName, artifactVersion, artifactReference)
  }

  private fun constraintStateForWithTransaction(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String,
    artifactReference: String,
    txn: DSLContext = jooq
  ): List<ConstraintState> {
    val environmentUID = environmentUidByName(deliveryConfigName, environmentName)
      ?: return emptyList()

    return sqlRetry.withRetry(READ) {
      txn
        .select(
          inline(deliveryConfigName).`as`("deliveryConfigName"),
          inline(environmentName).`as`("environmentName"),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES
        )
        .from(ENVIRONMENT_ARTIFACT_CONSTRAINT)
        .where(
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID.eq(environmentUID),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION.eq(artifactVersion),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_REFERENCE.eq(artifactReference)
        )
        .fetch { (
                   deliveryConfigName,
                   environmentName,
                   artifactVersion,
                   artifactReference,
                   constraintType,
                   status,
                   createdAt,
                   judgedBy,
                   judgedAt,
                   comment,
                   attributes
                 ) ->
          ConstraintState(
            deliveryConfigName,
            environmentName,
            artifactVersion,
            artifactReference,
            constraintType,
            status,
            createdAt,
            judgedBy,
            judgedAt,
            comment,
            objectMapper.readValue(attributes)
          )
        }
    }
  }

  override fun getPendingConstraintsForArtifactVersions(
    deliveryConfigName: String,
    environmentName: String,
    artifact: DeliveryArtifact
  ): List<PublishedArtifact> {
    val environmentUID = environmentUidByName(deliveryConfigName, environmentName)
      ?: return emptyList()

    return sqlRetry.withRetry(READ) {
      jooq
        .select(
          ARTIFACT_VERSIONS.NAME,
          ARTIFACT_VERSIONS.TYPE,
          ARTIFACT_VERSIONS.VERSION,
          ARTIFACT_VERSIONS.RELEASE_STATUS,
          ARTIFACT_VERSIONS.CREATED_AT,
          ARTIFACT_VERSIONS.GIT_METADATA,
          ARTIFACT_VERSIONS.BUILD_METADATA
        )
        .from(ENVIRONMENT_ARTIFACT_CONSTRAINT, ARTIFACT_VERSIONS)
        .where(
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID.eq(environmentUID),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS.eq(ConstraintStatus.PENDING)
        )
        .and(ARTIFACT_VERSIONS.NAME.eq(artifact.name))
        .and(ARTIFACT_VERSIONS.TYPE.eq(artifact.type))
        .and(ARTIFACT_VERSIONS.VERSION.eq(ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION))
        .fetchSortedArtifactVersions(artifact)
    }
  }

  override fun getArtifactVersionsQueuedForApproval(
    deliveryConfigName: String,
    environmentName: String,
    artifact: DeliveryArtifact
  ): List<PublishedArtifact> {
    val environmentUID = environmentUidByName(deliveryConfigName, environmentName)
      ?: return emptyList()

    return sqlRetry.withRetry(READ) {
      jooq.select(
        ARTIFACT_VERSIONS.NAME,
        ARTIFACT_VERSIONS.TYPE,
        ARTIFACT_VERSIONS.VERSION,
        ARTIFACT_VERSIONS.RELEASE_STATUS,
        ARTIFACT_VERSIONS.CREATED_AT,
        ARTIFACT_VERSIONS.GIT_METADATA,
        ARTIFACT_VERSIONS.BUILD_METADATA
      )
        .from(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL, ARTIFACT_VERSIONS)
        .where(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ENVIRONMENT_UID.eq(environmentUID))
        .and(
          ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ARTIFACT_REFERENCE.eq(artifact.reference)
            // for backward comparability
            .or(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ARTIFACT_REFERENCE.isNull)
        )
        .and(ARTIFACT_VERSIONS.VERSION.eq(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ARTIFACT_VERSION))
        .and(ARTIFACT_VERSIONS.NAME.eq(artifact.name))
        .and(ARTIFACT_VERSIONS.TYPE.eq(artifact.type))
        .fetchSortedArtifactVersions(artifact)
    }
  }

  /**
   * Used in the [EnvironmentConstraintRunner] to queue artifact versions for approval by the
   * [EnvironmentPromotionChecker].
   */
  override fun queueArtifactVersionForApproval(
    deliveryConfigName: String,
    environmentName: String,
    artifact: DeliveryArtifact,
    artifactVersion: String
  ) {
    sqlRetry.withRetry(WRITE) {
      environmentUidByName(deliveryConfigName, environmentName)
        ?.also { envUid ->
          jooq.insertInto(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL)
            .set(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ENVIRONMENT_UID, envUid)
            .set(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ARTIFACT_VERSION, artifactVersion)
            .set(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.QUEUED_AT, clock.instant())
            .set(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ARTIFACT_REFERENCE, artifact.reference)
            .onDuplicateKeyIgnore()
            .execute()
        }
    }
  }

  override fun deleteArtifactVersionQueuedForApproval(
    deliveryConfigName: String,
    environmentName: String,
    artifact: DeliveryArtifact,
    artifactVersion: String
  ) {
    sqlRetry.withRetry(WRITE) {
      environmentUidByName(deliveryConfigName, environmentName)
        ?.also { envUid ->
          jooq.deleteFrom(ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL)
            .where(
              ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ENVIRONMENT_UID.eq(envUid),
              ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ARTIFACT_VERSION.eq(artifactVersion),
              ENVIRONMENT_ARTIFACT_QUEUED_APPROVAL.ARTIFACT_REFERENCE.eq(artifact.reference)
            )
            .execute()
        }
    }
  }

  override fun triggerRecheck(application: String) {
    log.info("Triggering delivery config recheck for application $application")
    val uid = sqlRetry.withRetry(READ) {
      jooq.select(DELIVERY_CONFIG.UID)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.APPLICATION.eq(application))
        .fetchOne(DELIVERY_CONFIG.UID)
    }

    if (uid == null) {
      log.error("Config for app $application does not exist, cannot recheck it")
      return
    }

    sqlRetry.withRetry(WRITE) {
      jooq.update(DELIVERY_CONFIG_LAST_CHECKED)
        .set(DELIVERY_CONFIG_LAST_CHECKED.AT, EPOCH.plusSeconds(1))
        .set(DELIVERY_CONFIG_LAST_CHECKED.LEASED_AT, EPOCH.plusSeconds(1))
        .set(DELIVERY_CONFIG_LAST_CHECKED.LEASED_BY, RECHECK_LEASE_NAME)
        .where(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID.eq(uid))
        .execute()
    }
  }

  override fun itemsDueForCheck(
    minTimeSinceLastCheck: Duration,
    limit: Int
  ): Collection<DeliveryConfig> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck)
    return sqlRetry.withRetry(WRITE) {
      jooq.inTransaction {
        select(DELIVERY_CONFIG.UID, DELIVERY_CONFIG.NAME, DELIVERY_CONFIG_LAST_CHECKED.AT)
          .from(DELIVERY_CONFIG, DELIVERY_CONFIG_LAST_CHECKED)
          .where(DELIVERY_CONFIG.UID.eq(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID))
          // has not been checked recently
          .and(DELIVERY_CONFIG_LAST_CHECKED.AT.lessOrEqual(cutoff))
          // either no other Keel instance is working on this, or the lease has expired (e.g. due to
          // instance termination mid-check)
          .and(
            DELIVERY_CONFIG_LAST_CHECKED.LEASED_BY.isNull
              .or(DELIVERY_CONFIG_LAST_CHECKED.LEASED_AT.lessOrEqual(cutoff))
          )
          // the application is not paused
          .andNotExists(
            selectOne()
              .from(PAUSED)
              .where(PAUSED.NAME.eq(DELIVERY_CONFIG.APPLICATION))
              .and(PAUSED.SCOPE.eq(APPLICATION))
          )
          .orderBy(DELIVERY_CONFIG_LAST_CHECKED.AT)
          .limit(limit)
          .forUpdate()
          .fetch()
          .also {
            it.forEach { (uid, name, lastCheckedAt) ->
              update(DELIVERY_CONFIG_LAST_CHECKED)
                .set(DELIVERY_CONFIG_LAST_CHECKED.LEASED_BY, InetAddress.getLocalHost().hostName)
                .set(DELIVERY_CONFIG_LAST_CHECKED.LEASED_AT, now)
                .where(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID.eq(uid))
                .execute()
              publisher.publishEvent(
                AboutToBeChecked(
                  lastCheckedAt,
                  "deliveryConfig",
                  "deliveryConfig:$name"
                )
              )
            }
          }
      }
    }
      .map { (_, name) ->
        get(name)
      }
  }

  override fun markCheckComplete(deliveryConfig: DeliveryConfig, status: Any?) {
    sqlRetry.withRetry(WRITE) {
      jooq
        .update(DELIVERY_CONFIG_LAST_CHECKED)
        // set last check time to now
        .set(DELIVERY_CONFIG_LAST_CHECKED.AT, clock.instant())
        // clear the lease to allow other instances to check this item
        .setNull(DELIVERY_CONFIG_LAST_CHECKED.LEASED_BY)
        .setNull(DELIVERY_CONFIG_LAST_CHECKED.LEASED_AT)
        .where(
          DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID.eq(
            select(DELIVERY_CONFIG.UID)
              .from(DELIVERY_CONFIG)
              .where(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
          )
        )
        .and(DELIVERY_CONFIG_LAST_CHECKED.LEASED_BY.ne(RECHECK_LEASE_NAME))
        .execute()
    }
  }

  private val String.uids: Select<Record1<String>>
    get() = select(RESOURCE.UID)
      .from(RESOURCE)
      .where(RESOURCE.ID.eq(this))

  private fun deliveryConfigUidByName(deliveryConfigName: String) = sqlRetry.withRetry(READ) {
    jooq.select(DELIVERY_CONFIG.UID)
      .from(DELIVERY_CONFIG)
      .where(DELIVERY_CONFIG.NAME.eq(deliveryConfigName))
      .fetchOne(DELIVERY_CONFIG.UID)
  }

  private fun environmentUidByName(deliveryConfigName: String, environmentName: String): String? =
    sqlRetry.withRetry(READ) {
      jooq
        .select(ENVIRONMENT.UID)
        .from(DELIVERY_CONFIG)
        .innerJoin(ENVIRONMENT).on(DELIVERY_CONFIG.UID.eq(ENVIRONMENT.DELIVERY_CONFIG_UID))
        .where(
          DELIVERY_CONFIG.NAME.eq(deliveryConfigName),
          ENVIRONMENT.NAME.eq(environmentName)
        )
        .fetchOne(ENVIRONMENT.UID)
    }

  private fun envUid(deliveryConfigName: String, environmentName: String): Select<Record1<String>> =
    select(ENVIRONMENT.UID)
      .from(DELIVERY_CONFIG)
      .innerJoin(ENVIRONMENT).on(DELIVERY_CONFIG.UID.eq(ENVIRONMENT.DELIVERY_CONFIG_UID))
      .where(
        DELIVERY_CONFIG.NAME.eq(deliveryConfigName),
        ENVIRONMENT.NAME.eq(environmentName)
      )

  private fun applicationByDeliveryConfigName(name: String): String =
    sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_CONFIG.APPLICATION)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.NAME.eq(name))
        .fetchOne(DELIVERY_CONFIG.APPLICATION)
    }
      ?: throw NoSuchDeliveryConfigName(name)

  private fun <T : Any> T.toJson() = objectMapper.writeValueAsString(this)

  override fun getApplicationSummaries(): Collection<ApplicationSummary> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          DELIVERY_CONFIG.UID,
          DELIVERY_CONFIG.NAME,
          DELIVERY_CONFIG.APPLICATION,
          DELIVERY_CONFIG.SERVICE_ACCOUNT,
          DELIVERY_CONFIG.API_VERSION,
          count(ACTIVE_RESOURCE.UID),
          PAUSED.NAME
        )
        .from(DELIVERY_CONFIG)
        .leftOuterJoin(ACTIVE_RESOURCE)
        .on(ACTIVE_RESOURCE.APPLICATION.eq(DELIVERY_CONFIG.APPLICATION))
        .leftOuterJoin(PAUSED)
        .on(PAUSED.NAME.eq(DELIVERY_CONFIG.APPLICATION).and(PAUSED.SCOPE.eq(APPLICATION)))
        .groupBy(DELIVERY_CONFIG.APPLICATION)
        .orderBy(DELIVERY_CONFIG.APPLICATION)
        .fetch { (uid, name, application, serviceAccount, apiVersion, resourceCount, paused) ->
          ApplicationSummary(
            deliveryConfigName = name,
            application = application,
            serviceAccount = serviceAccount,
            apiVersion = apiVersion,
            createdAt = ULID.parseULID(uid).timestampAsInstant(),
            resourceCount = resourceCount,
            isPaused = paused != null
          )
        }
    }

  override fun deliveryConfigLastChecked(deliveryConfig: DeliveryConfig): Instant =
    sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_CONFIG_LAST_CHECKED.AT)
        .from(DELIVERY_CONFIG_LAST_CHECKED, DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))
        .and(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
        .fetchSingle(DELIVERY_CONFIG_LAST_CHECKED.AT) ?: EPOCH
    }

  override fun addArtifactVersionToEnvironment(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifact: DeliveryArtifact,
    version: String,
  ) {
    val environmentUid = envUid(deliveryConfig.name, environmentName)
    sqlRetry.withRetry(WRITE) {
      val alreadyExists = jooq
        .selectCount()
        .from(ENVIRONMENT_VERSION_ARTIFACT_VERSION)
        .where(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_UID.eq(environmentUid))
        .and(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_UID.eq(artifact.uid))
        .and(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_VERSION.eq(version))
        .fetchSingleInto<Int>() > 0

      if (alreadyExists) {
        // this method should never be called in this case, log it and do nothing
        log.error(
          "Artifact {} version {} is already associated with {}/{}",
          artifact.name,
          version,
          deliveryConfig.application,
          environmentName
        )
      } else {
        val currentVersion = jooq
          .select(coalesce(max(ENVIRONMENT_VERSION.VERSION), value(0)))
          .from(ENVIRONMENT_VERSION)
          .where(ENVIRONMENT_VERSION.ENVIRONMENT_UID.eq(environmentUid))
          .fetchSingleInto<Int>()
        val newVersion = currentVersion + 1

        log.debug(
          "Creating a new version {} of environment {}/{} because of a new version {} of artifact {}",
          newVersion,
          deliveryConfig.application,
          environmentName,
          version,
          artifact.name
        )

        jooq.inTransaction {
          // insert a new environment version
          insertInto(ENVIRONMENT_VERSION)
            .set(ENVIRONMENT_VERSION.ENVIRONMENT_UID, environmentUid)
            .set(ENVIRONMENT_VERSION.VERSION, newVersion)
            .set(ENVIRONMENT_VERSION.CREATED_AT, clock.instant())
            .execute()

          // make the new environment version 'active'
          setActiveEnvironmentVersion(environmentUid, newVersion)

          // copy all existing resource versions to the new environment version
          insertInto(ENVIRONMENT_RESOURCE)
            .columns(
              ENVIRONMENT_RESOURCE.ENVIRONMENT_UID,
              ENVIRONMENT_RESOURCE.ENVIRONMENT_VERSION,
              ENVIRONMENT_RESOURCE.RESOURCE_UID,
              ENVIRONMENT_RESOURCE.RESOURCE_VERSION
            )
            .select(
              select(
                ENVIRONMENT_RESOURCE.ENVIRONMENT_UID,
                value(newVersion),
                ENVIRONMENT_RESOURCE.RESOURCE_UID,
                ENVIRONMENT_RESOURCE.RESOURCE_VERSION
              )
                .from(ENVIRONMENT_RESOURCE)
                .where(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(environmentUid))
                .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_VERSION.eq(currentVersion))
            )
            .execute()

          // add the new artifact version to the new environment version
          insertInto(ENVIRONMENT_VERSION_ARTIFACT_VERSION)
            .set(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_UID, environmentUid)
            .set(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_VERSION, newVersion)
            .set(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_UID, artifact.uid)
            .set(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_VERSION, version)
            .execute()

          // copy any other artifact versions to the new environment version
          insertInto(ENVIRONMENT_VERSION_ARTIFACT_VERSION)
            .columns(
              ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_UID,
              ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_VERSION,
              ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_UID,
              ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_VERSION
            )
            .select(
              select(
                ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_UID,
                value(newVersion),
                ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_UID,
                ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_VERSION
              )
                .from(ENVIRONMENT_VERSION_ARTIFACT_VERSION)
                .where(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_UID.eq(environmentUid))
                .and(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ENVIRONMENT_VERSION.eq(currentVersion))
                .and(ENVIRONMENT_VERSION_ARTIFACT_VERSION.ARTIFACT_UID.ne(artifact.uid))
            )
            .execute()
        }
      }
    }
  }

  private fun DSLContext.setActiveEnvironmentVersion(uid: Select<Record1<String>>, version: Int) {
    insertInto(ACTIVE_ENVIRONMENT_VERSION)
      .set(ACTIVE_ENVIRONMENT_VERSION.ENVIRONMENT_UID, uid)
      .set(ACTIVE_ENVIRONMENT_VERSION.ACTIVE_VERSION, version)
      .set(ACTIVE_ENVIRONMENT_VERSION.ACTIVE_SINCE, clock.instant())
      .onDuplicateKeyUpdate()
      .set(ACTIVE_ENVIRONMENT_VERSION.ACTIVE_VERSION, version)
      .set(ACTIVE_ENVIRONMENT_VERSION.ACTIVE_SINCE, clock.instant())
      .where(ACTIVE_ENVIRONMENT_VERSION.ENVIRONMENT_UID.eq(uid))
      .execute()
  }

  private fun DSLContext.setActiveEnvironmentVersion(uid: String, version: Int) {
    insertInto(ACTIVE_ENVIRONMENT_VERSION)
      .set(ACTIVE_ENVIRONMENT_VERSION.ENVIRONMENT_UID, uid)
      .set(ACTIVE_ENVIRONMENT_VERSION.ACTIVE_VERSION, version)
      .set(ACTIVE_ENVIRONMENT_VERSION.ACTIVE_SINCE, clock.instant())
      .onDuplicateKeyUpdate()
      .set(ACTIVE_ENVIRONMENT_VERSION.ACTIVE_VERSION, version)
      .set(ACTIVE_ENVIRONMENT_VERSION.ACTIVE_SINCE, clock.instant())
      .where(ACTIVE_ENVIRONMENT_VERSION.ENVIRONMENT_UID.eq(uid))
      .execute()
  }

  private val DeliveryArtifact.uid
    get() = jooq
      .select(DELIVERY_ARTIFACT.UID)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName))
      .and(DELIVERY_ARTIFACT.REFERENCE.eq(reference))

  companion object {
    private const val DELETE_CHUNK_SIZE = 20
  }
}
