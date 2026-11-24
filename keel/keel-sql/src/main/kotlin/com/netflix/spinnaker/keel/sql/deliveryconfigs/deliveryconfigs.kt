package com.netflix.spinnaker.keel.sql.deliveryconfigs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.core.api.timestampAsInstant
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter.ATTACH_ALL
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter.ATTACH_ARTIFACTS
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter.ATTACH_ENVIRONMENTS
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter.ATTACH_PREVIEW_ENVIRONMENTS
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTIVE_ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.PREVIEW_ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTIVE_RESOURCE
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.SqlStorageContext
import com.netflix.spinnaker.keel.sql.mapToArtifact
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Record9
import org.jooq.Select
import org.jooq.impl.DSL.select

internal fun SqlStorageContext.deliveryConfigByName(
  name: String,
  vararg dependentAttachFilter: DependentAttachFilter = arrayOf(ATTACH_ALL)
): DeliveryConfig =
  sqlRetry.withRetry(READ) {
    jooq.select(
      DELIVERY_CONFIG.UID,
      DELIVERY_CONFIG.NAME,
      DELIVERY_CONFIG.APPLICATION,
      DELIVERY_CONFIG.SERVICE_ACCOUNT,
      DELIVERY_CONFIG.METADATA,
      DELIVERY_CONFIG.RAW_CONFIG
    )
      .from(DELIVERY_CONFIG)
      .where(DELIVERY_CONFIG.NAME.eq(name))
      .fetchOne { (uid, name, application, serviceAccount, metadata, rawConfig) ->
        uid to DeliveryConfig(
          name = name,
          application = application,
          serviceAccount = serviceAccount,
          metadata = (metadata ?: emptyMap()) + mapOf("createdAt" to ULID.parseULID(uid).timestampAsInstant()),
          rawConfig = rawConfig
        )
      }
      ?.let { (_, deliveryConfig) ->
        attachDependents(deliveryConfig, *dependentAttachFilter)
      }
      ?: throw NoSuchDeliveryConfigName(name)
  }

internal fun SqlStorageContext.attachDependents(
  deliveryConfig: DeliveryConfig,
  vararg dependentAttachFilter: DependentAttachFilter = arrayOf(ATTACH_ALL)
): DeliveryConfig {
  val artifacts = if (ATTACH_ALL in dependentAttachFilter || ATTACH_ARTIFACTS in dependentAttachFilter) {
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          DELIVERY_ARTIFACT.NAME,
          DELIVERY_ARTIFACT.TYPE,
          DELIVERY_ARTIFACT.DETAILS,
          DELIVERY_ARTIFACT.REFERENCE,
          DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME
        )
        .from(DELIVERY_ARTIFACT, DELIVERY_CONFIG_ARTIFACT)
        .where(DELIVERY_CONFIG_ARTIFACT.ARTIFACT_UID.eq(DELIVERY_ARTIFACT.UID))
        .and(DELIVERY_CONFIG_ARTIFACT.DELIVERY_CONFIG_UID.eq(deliveryConfig.uid))
        .fetch { (name, type, details, reference, configName) ->
          mapToArtifact(
            artifactSuppliers.supporting(type),
            name,
            type.toLowerCase(),
            details,
            reference,
            configName
          )
        }
    }
  } else {
    null
  }

  val environments = if (ATTACH_ALL in dependentAttachFilter || ATTACH_ENVIRONMENTS in dependentAttachFilter) {
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          ACTIVE_ENVIRONMENT.UID,
          ACTIVE_ENVIRONMENT.NAME,
          ACTIVE_ENVIRONMENT.VERSION,
          ACTIVE_ENVIRONMENT.IS_PREVIEW,
          ACTIVE_ENVIRONMENT.CONSTRAINTS,
          ACTIVE_ENVIRONMENT.NOTIFICATIONS,
          ACTIVE_ENVIRONMENT.VERIFICATIONS,
          ACTIVE_ENVIRONMENT.POST_DEPLOY_ACTIONS,
          ACTIVE_ENVIRONMENT.METADATA
        )
        .from(ACTIVE_ENVIRONMENT)
        .where(ACTIVE_ENVIRONMENT.DELIVERY_CONFIG_UID.eq(deliveryConfig.uid))
        .fetch { record ->
          makeEnvironment(record, objectMapper)
        }
    }
  } else {
    null
  }

  val previewEnvironments = if (ATTACH_ALL in dependentAttachFilter || ATTACH_PREVIEW_ENVIRONMENTS in dependentAttachFilter) {
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          ACTIVE_ENVIRONMENT.NAME,
          PREVIEW_ENVIRONMENT.BRANCH_FILTER,
          PREVIEW_ENVIRONMENT.NOTIFICATIONS,
          PREVIEW_ENVIRONMENT.VERIFICATIONS
        )
        .from(PREVIEW_ENVIRONMENT)
        .innerJoin(ACTIVE_ENVIRONMENT)
        .on(ACTIVE_ENVIRONMENT.DELIVERY_CONFIG_UID.eq(PREVIEW_ENVIRONMENT.DELIVERY_CONFIG_UID))
        .and(ACTIVE_ENVIRONMENT.UID.eq(PREVIEW_ENVIRONMENT.BASE_ENVIRONMENT_UID))
        .where(PREVIEW_ENVIRONMENT.DELIVERY_CONFIG_UID.eq(deliveryConfig.uid))
        .fetch { (baseEnvName, branchFilterJson, notificationsJson, verifyWithJson) ->
          PreviewEnvironmentSpec(
            baseEnvironment = baseEnvName,
            branch = objectMapper.readValue(branchFilterJson),
            notifications = notificationsJson?.let { objectMapper.readValue(it) } ?: emptySet(),
            verifyWith = verifyWithJson?.let { objectMapper.readValue(it) } ?: emptyList()
          )
        }
    }
  } else {
    null
  }

  return deliveryConfig.copy(
    artifacts = artifacts?.toSet() ?: deliveryConfig.artifacts,
    environments = environments?.toSet() ?: deliveryConfig.environments,
    previewEnvironments = previewEnvironments?.toSet() ?: deliveryConfig.previewEnvironments
  )
}

internal fun SqlStorageContext.resourcesForEnvironment(uid: String) =
  sqlRetry.withRetry(READ) {
    jooq
      .select(
        ACTIVE_RESOURCE.KIND,
        ACTIVE_RESOURCE.METADATA,
        ACTIVE_RESOURCE.SPEC
      )
      .from(ACTIVE_RESOURCE, ENVIRONMENT_RESOURCE, ACTIVE_ENVIRONMENT)
      .where(ACTIVE_RESOURCE.UID.eq(ENVIRONMENT_RESOURCE.RESOURCE_UID))
      .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(ACTIVE_ENVIRONMENT.UID))
      .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_VERSION.eq(ACTIVE_ENVIRONMENT.VERSION))
      .and(ACTIVE_ENVIRONMENT.UID.eq(uid))
      .fetch { (kind, metadata, spec) ->
        resourceFactory.create(kind, metadata, spec)
      }
  }
    .toSet()

/**
 * A query that selects the UID for a [DeliveryConfig] based on its [DeliveryConfig.name].
 */
internal val DeliveryConfig.uid: Select<Record1<String>>
  get() = select(DELIVERY_CONFIG.UID)
    .from(DELIVERY_CONFIG)
    .where(DELIVERY_CONFIG.NAME.eq(name))

/** Helper function to select all required fields to make an environment. Use along with [makeEnvironment] */
internal fun DSLContext.selectEnvironmentColumns() =
  select(
    ACTIVE_ENVIRONMENT.UID,
    ACTIVE_ENVIRONMENT.NAME,
    ACTIVE_ENVIRONMENT.VERSION,
    ACTIVE_ENVIRONMENT.IS_PREVIEW,
    ACTIVE_ENVIRONMENT.CONSTRAINTS,
    ACTIVE_ENVIRONMENT.NOTIFICATIONS,
    ACTIVE_ENVIRONMENT.VERIFICATIONS,
    ACTIVE_ENVIRONMENT.POST_DEPLOY_ACTIONS,
    ACTIVE_ENVIRONMENT.METADATA
  )

/** Helper function to construct an [Environment] off a record from [ACTIVE_ENVIRONMENT]. */
internal fun SqlStorageContext.makeEnvironment(
  record: Record9<String, String, Int, Boolean, String, String, String, String, String>,
  objectMapper: ObjectMapper
): Environment {
  val (environmentUid, name, _, isPreview, constraintsJson, notificationsJson, verifyWithJson, postDeployActionsJson, metadataJson) = record
  return Environment(
    name = name,
    isPreview = isPreview,
    resources = resourcesForEnvironment(environmentUid),
    constraints = objectMapper.readValue(constraintsJson),
    notifications = notificationsJson?.let { objectMapper.readValue(it) } ?: emptySet(),
    verifyWith = verifyWithJson?.let { objectMapper.readValue(it) } ?: emptyList(),
    postDeploy = postDeployActionsJson?.let { objectMapper.readValue(it) } ?: emptyList()
  ).apply {
    addMetadata(metadataJson?.let { objectMapper.readValue(it) } ?: emptyMap())
  }
}
