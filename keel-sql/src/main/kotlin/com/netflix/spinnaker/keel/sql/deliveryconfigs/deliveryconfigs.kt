package com.netflix.spinnaker.keel.sql.deliveryconfigs

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
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.LATEST_ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.PREVIEW_ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE_WITH_METADATA
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.SqlStorageContext
import com.netflix.spinnaker.keel.sql.mapToArtifact
import de.huxhorn.sulky.ulid.ULID
import org.jooq.Record1
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
      DELIVERY_CONFIG.METADATA
    )
      .from(DELIVERY_CONFIG)
      .where(DELIVERY_CONFIG.NAME.eq(name))
      .fetchOne { (uid, name, application, serviceAccount, metadata) ->
        uid to DeliveryConfig(
          name = name,
          application = application,
          serviceAccount = serviceAccount,
          metadata = (metadata ?: emptyMap()) + mapOf("createdAt" to ULID.parseULID(uid).timestampAsInstant())
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
          LATEST_ENVIRONMENT.UID,
          LATEST_ENVIRONMENT.NAME,
          LATEST_ENVIRONMENT.VERSION,
          LATEST_ENVIRONMENT.CONSTRAINTS,
          LATEST_ENVIRONMENT.NOTIFICATIONS,
          LATEST_ENVIRONMENT.VERIFICATIONS
        )
        .from(LATEST_ENVIRONMENT)
        .where(LATEST_ENVIRONMENT.DELIVERY_CONFIG_UID.eq(deliveryConfig.uid))
        .fetch { (environmentUid, name, _, constraintsJson, notificationsJson, verifyWithJson) ->
          Environment(
            name = name,
            resources = resourcesForEnvironment(environmentUid),
            constraints = objectMapper.readValue(constraintsJson),
            notifications = notificationsJson?.let { objectMapper.readValue(it) } ?: emptySet(),
            verifyWith = verifyWithJson?.let {
              objectMapper.readValue(it)
            } ?: emptyList()
          )
        }
    }
  } else {
    null
  }

  val previewEnvironments = if (ATTACH_ALL in dependentAttachFilter || ATTACH_PREVIEW_ENVIRONMENTS in dependentAttachFilter) {
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          LATEST_ENVIRONMENT.NAME,
          PREVIEW_ENVIRONMENT.BRANCH_FILTER,
          PREVIEW_ENVIRONMENT.NOTIFICATIONS,
          PREVIEW_ENVIRONMENT.VERIFICATIONS
        )
        .from(PREVIEW_ENVIRONMENT)
        .innerJoin(LATEST_ENVIRONMENT)
        .on(LATEST_ENVIRONMENT.DELIVERY_CONFIG_UID.eq(PREVIEW_ENVIRONMENT.DELIVERY_CONFIG_UID))
        .and(LATEST_ENVIRONMENT.UID.eq(PREVIEW_ENVIRONMENT.BASE_ENVIRONMENT_UID))
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
        RESOURCE_WITH_METADATA.KIND,
        RESOURCE_WITH_METADATA.METADATA,
        RESOURCE_WITH_METADATA.SPEC
      )
      .from(RESOURCE_WITH_METADATA, ENVIRONMENT_RESOURCE, LATEST_ENVIRONMENT)
      .where(RESOURCE_WITH_METADATA.UID.eq(ENVIRONMENT_RESOURCE.RESOURCE_UID))
      .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(LATEST_ENVIRONMENT.UID))
      .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_VERSION.eq(LATEST_ENVIRONMENT.VERSION))
      .and(LATEST_ENVIRONMENT.UID.eq(uid))
      .fetch { (kind, metadata, spec) ->
        resourceFactory.invoke(kind, metadata, spec)
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
