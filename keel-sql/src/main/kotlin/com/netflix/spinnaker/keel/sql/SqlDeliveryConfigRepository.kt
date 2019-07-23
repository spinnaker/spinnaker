package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.jooq.DSLContext

class SqlDeliveryConfigRepository(
  private val jooq: DSLContext,
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
              .execute()
          }
        environment.resources.forEach { resource ->
          jooq.insertInto(ENVIRONMENT_RESOURCE)
            .set(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID, environmentUID)
            .set(ENVIRONMENT_RESOURCE.RESOURCE_UID, resource.uid.toString())
            .onDuplicateKeyIgnore()
            .execute()
        }
      }
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
              .select(ENVIRONMENT.UID, ENVIRONMENT.NAME)
              .from(ENVIRONMENT)
              .where(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(uid))
              .fetch { (environmentUid, name) ->
                environmentUid to Environment(name, emptySet())
              }
              .mapTo(mutableSetOf()) { (environmentUid, environment) ->
                jooq
                  .select(
                    RESOURCE.API_VERSION,
                    RESOURCE.KIND,
                    RESOURCE.METADATA,
                    RESOURCE.SPEC
                  )
                  .from(RESOURCE, ENVIRONMENT_RESOURCE)
                  .where(RESOURCE.UID.eq(ENVIRONMENT_RESOURCE.RESOURCE_UID))
                  .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(environmentUid))
                  .fetch { (apiVersion, kind, metadata, spec) ->
                    Resource(
                      ApiVersion(apiVersion),
                      kind,
                      mapper.readValue(metadata),
                      mapper.readValue(spec, resourceTypeIdentifier.identify(ApiVersion(apiVersion), kind))
                    )
                  }
                  .let { resources ->
                    environment.copy(resources = resources.toSet())
                  }
              }
              .let { environments ->
                deliveryConfig.copy(
                  artifacts = artifacts,
                  environments = environments
                )
              }
          }
      }
      ?: throw NoSuchDeliveryConfigName(name)
}
