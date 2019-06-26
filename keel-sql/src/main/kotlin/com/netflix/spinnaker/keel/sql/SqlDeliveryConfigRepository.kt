package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.jooq.DSLContext

class SqlDeliveryConfigRepository(
  private val jooq: DSLContext,
  private val resourceTypeIdentifier: (String) -> Class<*>
) : DeliveryConfigRepository {

  private val mapper = configuredObjectMapper()

  override fun store(deliveryConfig: DeliveryConfig) {
    jooq.inTransaction {
      val uid = randomUID().toString()
      with(deliveryConfig) {
        insertInto(Schema.DeliveryConfig.table)
          .set(Schema.DeliveryConfig.uid, uid)
          .set(Schema.DeliveryConfig.name, name)
          .set(Schema.DeliveryConfig.application, application)
          .onDuplicateKeyIgnore()
          .execute()
        artifacts.forEach { artifact ->
          insertInto(Schema.Artifact.table)
            .set(Schema.Artifact.uid, randomUID().toString())
            .set(Schema.Artifact.name, artifact.name)
            .set(Schema.Artifact.type, artifact.type.name)
            .onDuplicateKeyIgnore()
            .execute()
          insertInto(Schema.DeliveryConfigArtifact.table)
            .set(Schema.DeliveryConfigArtifact.deliveryConfigUid, uid)
            .set(Schema.DeliveryConfigArtifact.artifactUid, select(Schema.Artifact.uid).from(Schema.Artifact.table).where(Schema.Artifact.name.eq(artifact.name)))
            .onDuplicateKeyIgnore()
            .execute()
        }
        environments.forEach { environment ->
          val environmentUID = randomUID().toString()
          insertInto(Schema.Environment.table)
            .set(Schema.Environment.uid, environmentUID)
            .set(Schema.Environment.deliveryConfigUid, uid)
            .set(Schema.Environment.name, environment.name)
            .onDuplicateKeyIgnore()
            .execute()
          environment.resources.forEach { resource ->
            insertInto(Schema.Resource.table)
              .set(Schema.Resource.uid, resource.uid.toString())
              .set(Schema.Resource.apiVersion, resource.apiVersion.toString())
              .set(Schema.Resource.kind, resource.kind)
              .set(Schema.Resource.name, resource.name.value)
              .set(Schema.Resource.metadata, mapper.writeValueAsString(resource.metadata))
              .set(Schema.Resource.spec, mapper.writeValueAsString(resource.spec))
              .onDuplicateKeyIgnore()
              .execute()
            insertInto(Schema.EnvironmentResource.table)
              .set(Schema.EnvironmentResource.environmentUid, environmentUID)
              .set(Schema.EnvironmentResource.resourceUid, resource.uid.toString())
              .onDuplicateKeyIgnore()
              .execute()
          }
        }
      }
    }
  }

  override fun get(name: String): DeliveryConfig =
    jooq
      .select(
        Schema.DeliveryConfig.uid,
        Schema.DeliveryConfig.name,
        Schema.DeliveryConfig.application
      )
      .from(Schema.DeliveryConfig.table)
      .where(Schema.DeliveryConfig.name.eq(name))
      .fetchOne { (uid, name, application) ->
        uid to DeliveryConfig(name, application)
      }
      ?.let { (uid, deliveryConfig) ->
        jooq
          .select(Schema.Artifact.name, Schema.Artifact.type)
          .from(Schema.Artifact.table, Schema.DeliveryConfigArtifact.table)
          .where(Schema.DeliveryConfigArtifact.artifactUid.eq(Schema.Artifact.uid))
          .and(Schema.DeliveryConfigArtifact.deliveryConfigUid.eq(uid))
          .fetch { (name, type) ->
            DeliveryArtifact(name, ArtifactType.valueOf(type))
          }
          .let { artifacts ->
            jooq
              .select(Schema.Environment.uid, Schema.Environment.name)
              .from(Schema.Environment.table)
              .where(Schema.Environment.deliveryConfigUid.eq(uid))
              .fetch { (environmentUid, name) ->
                environmentUid to Environment(name, emptySet())
              }
              .map { (environmentUid, environment) ->
                jooq
                  .select(
                    Schema.Resource.apiVersion,
                    Schema.Resource.kind,
                    Schema.Resource.metadata,
                    Schema.Resource.spec
                  )
                  .from(Schema.Resource.table, Schema.EnvironmentResource.table)
                  .where(Schema.Resource.uid.eq(Schema.EnvironmentResource.resourceUid))
                  .and(Schema.EnvironmentResource.environmentUid.eq(environmentUid))
                  .fetch { (apiVersion, kind, metadata, spec) ->
                    Resource(
                      ApiVersion(apiVersion),
                      kind,
                      mapper.readValue(metadata),
                      mapper.readValue(spec, resourceTypeIdentifier(kind))
                    )
                  }
                  .let { resources ->
                    environment.copy(resources = resources.toSet())
                  }
              }
              .let { environments ->
                deliveryConfig.copy(
                  artifacts = artifacts.toSet(),
                  environments = environments.toSet()
                )
              }
          }
      }
      ?: throw NoSuchDeliveryConfigName(name)
}
