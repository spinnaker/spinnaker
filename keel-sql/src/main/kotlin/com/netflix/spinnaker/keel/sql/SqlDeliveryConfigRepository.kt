package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG_LAST_CHECKED
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.jooq.DSLContext
import org.jooq.impl.DSL.select
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH

class SqlDeliveryConfigRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val resourceTypeIdentifier: ResourceTypeIdentifier
) : DeliveryConfigRepository {

  private val mapper = configuredObjectMapper()

  override fun store(deliveryConfig: DeliveryConfig) {
    with(deliveryConfig) {
      val uid = jooq
        .select(DELIVERY_CONFIG.UID)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.NAME.eq(name))
        .fetchOne(DELIVERY_CONFIG.UID)
        ?: randomUID().toString()
      jooq.insertInto(DELIVERY_CONFIG)
        .set(DELIVERY_CONFIG.UID, uid)
        .set(DELIVERY_CONFIG.NAME, name)
        .set(DELIVERY_CONFIG.APPLICATION, application)
        .onDuplicateKeyIgnore()
        .execute()
      artifacts.forEach { artifact ->
        jooq.insertInto(DELIVERY_CONFIG_ARTIFACT)
          .set(DELIVERY_CONFIG_ARTIFACT.DELIVERY_CONFIG_UID, uid)
          .set(DELIVERY_CONFIG_ARTIFACT.ARTIFACT_UID, jooq.select(DELIVERY_ARTIFACT.UID).from(DELIVERY_ARTIFACT).where(DELIVERY_ARTIFACT.NAME.eq(artifact.name)))
          .onDuplicateKeyIgnore()
          .execute()
      }
      environments.forEach { environment ->
        val environmentUID = jooq
          .select(ENVIRONMENT.UID)
          .from(ENVIRONMENT)
          .where(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(uid))
          .and(ENVIRONMENT.NAME.eq(environment.name))
          .fetchOne(ENVIRONMENT.UID)
          ?: randomUID().toString().also {
            jooq.insertInto(ENVIRONMENT)
              .set(ENVIRONMENT.UID, it)
              .set(ENVIRONMENT.DELIVERY_CONFIG_UID, uid)
              .set(ENVIRONMENT.NAME, environment.name)
              .set(ENVIRONMENT.CONSTRAINTS, mapper.writeValueAsString(environment.constraints))
              .onDuplicateKeyUpdate()
              .set(ENVIRONMENT.CONSTRAINTS, mapper.writeValueAsString(environment.constraints))
              .execute()
          }
        environment.resources.forEach { resource ->
          jooq.insertInto(ENVIRONMENT_RESOURCE)
            .set(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID, environmentUID)
            .set(ENVIRONMENT_RESOURCE.RESOURCE_UID, select(RESOURCE.UID).from(RESOURCE).where(RESOURCE.ID.eq(resource.id.value)))
            .onDuplicateKeyIgnore()
            .execute()
        }
      }
      jooq.insertInto(DELIVERY_CONFIG_LAST_CHECKED)
        .set(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID, uid)
        .set(DELIVERY_CONFIG_LAST_CHECKED.AT, EPOCH.plusSeconds(1).toLocal())
        .onDuplicateKeyUpdate()
        .set(DELIVERY_CONFIG_LAST_CHECKED.AT, EPOCH.plusSeconds(1).toLocal())
        .execute()
    }
  }

  override fun get(name: String): DeliveryConfig =
    jooq
      .select(
        DELIVERY_CONFIG.UID,
        DELIVERY_CONFIG.NAME,
        DELIVERY_CONFIG.APPLICATION
      )
      .from(DELIVERY_CONFIG)
      .where(DELIVERY_CONFIG.NAME.eq(name))
      .fetchOne { (uid, name, application) ->
        uid to DeliveryConfig(name, application)
      }
      ?.let { (uid, deliveryConfig) ->
        jooq
          .select(DELIVERY_ARTIFACT.NAME, DELIVERY_ARTIFACT.TYPE)
          .from(DELIVERY_ARTIFACT, DELIVERY_CONFIG_ARTIFACT)
          .where(DELIVERY_CONFIG_ARTIFACT.ARTIFACT_UID.eq(DELIVERY_ARTIFACT.UID))
          .and(DELIVERY_CONFIG_ARTIFACT.DELIVERY_CONFIG_UID.eq(uid))
          .fetch { (name, type) ->
            DeliveryArtifact(name, ArtifactType.valueOf(type))
          }
          .toSet()
          .let { artifacts ->
            jooq
              .select(ENVIRONMENT.UID, ENVIRONMENT.NAME, ENVIRONMENT.CONSTRAINTS)
              .from(ENVIRONMENT)
              .where(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(uid))
              .fetch { (environmentUid, name, constraintsJson) ->
                Environment(
                  name = name,
                  resources = resourcesForEnvironment(environmentUid),
                  constraints = mapper.readValue(constraintsJson)
                )
              }
              .let { environments ->
                deliveryConfig.copy(
                  artifacts = artifacts,
                  environments = environments.toSet()
                )
              }
          }
      }
      ?: throw NoSuchDeliveryConfigName(name)

  override fun environmentFor(resourceId: ResourceId): Environment? =
    jooq
      .select(ENVIRONMENT.UID, ENVIRONMENT.NAME, ENVIRONMENT.CONSTRAINTS)
      .from(ENVIRONMENT, ENVIRONMENT_RESOURCE, RESOURCE)
      .where(RESOURCE.ID.eq(resourceId.value))
      .and(ENVIRONMENT_RESOURCE.RESOURCE_UID.eq(RESOURCE.UID))
      .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
      .fetchOne { (uid, name, constraintsJson) ->
        Environment(
          name = name,
          resources = resourcesForEnvironment(uid),
          constraints = mapper.readValue(constraintsJson)
        )
      }

  override fun deliveryConfigFor(resourceId: ResourceId): DeliveryConfig? =
    // TODO: this implementation could be more efficient by sharing code with get(name)
    jooq
      .select(DELIVERY_CONFIG.NAME)
      .from(ENVIRONMENT, ENVIRONMENT_RESOURCE, RESOURCE, DELIVERY_CONFIG)
      .where(RESOURCE.ID.eq(resourceId.value))
      .and(ENVIRONMENT_RESOURCE.RESOURCE_UID.eq(RESOURCE.UID))
      .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
      .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))
      .fetchOne { (name) ->
        get(name)
      }

  private fun resourcesForEnvironment(uid: String) =
    jooq
      .select(
        RESOURCE.API_VERSION,
        RESOURCE.KIND,
        RESOURCE.METADATA,
        RESOURCE.SPEC
      )
      .from(RESOURCE, ENVIRONMENT_RESOURCE)
      .where(RESOURCE.UID.eq(ENVIRONMENT_RESOURCE.RESOURCE_UID))
      .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(uid))
      .fetch { (apiVersion, kind, metadata, spec) ->
        Resource(
          ApiVersion(apiVersion),
          kind,
          mapper.readValue<Map<String, Any?>>(metadata).asResourceMetadata(),
          mapper.readValue(spec, resourceTypeIdentifier.identify(ApiVersion(apiVersion), kind))
        )
      }
      .toSet()

  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryConfig> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck).toLocal()
    return jooq.inTransaction {
      select(DELIVERY_CONFIG.UID, DELIVERY_CONFIG.NAME)
        .from(DELIVERY_CONFIG, DELIVERY_CONFIG_LAST_CHECKED)
        .where(DELIVERY_CONFIG.UID.eq(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID))
        .and(DELIVERY_CONFIG_LAST_CHECKED.AT.lessOrEqual(cutoff))
        .orderBy(DELIVERY_CONFIG_LAST_CHECKED.AT)
        .limit(limit)
        .forUpdate()
        .fetch()
        .also {
          it.forEach { (uid, _) ->
            insertInto(DELIVERY_CONFIG_LAST_CHECKED)
              .set(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID, uid)
              .set(DELIVERY_CONFIG_LAST_CHECKED.AT, now.toLocal())
              .onDuplicateKeyUpdate()
              .set(DELIVERY_CONFIG_LAST_CHECKED.AT, now.toLocal())
              .execute()
          }
        }
        .map { (_, name) ->
          get(name)
        }
    }
  }

  private fun Instant.toLocal() = atZone(clock.zone).toLocalDateTime()
}
