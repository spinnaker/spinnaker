package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import java.sql.Timestamp

class SqlResourceRepository(
  private val jooq: DSLContext,
  private val objectMapper: ObjectMapper
) : ResourceRepository {

  override fun allResources(callback: (ResourceHeader) -> Unit) {
    jooq
      .select(UID, API_VERSION, KIND, NAME)
      .from(RESOURCE)
      .fetch()
      .map { (uid, apiVersion, kind, name) ->
        ResourceHeader(ULID.parseULID(uid), ResourceName(name), ApiVersion(apiVersion), kind)
      }
      .forEach(callback)
  }

  override fun <T : Any> get(uid: UID, specType: Class<T>): Resource<T> {
    return jooq
      .select(UID, API_VERSION, KIND, NAME, METADATA, SPEC)
      .from(RESOURCE)
      .where(UID.eq(uid.toString()))
      .fetchOne()
      ?.let { (uid, apiVersion, kind, name, metadata, spec) ->
        Resource(
          ApiVersion(apiVersion),
          kind,
          ResourceMetadata(
            ResourceName(name),
            ULID.parseULID(uid),
            objectMapper.readValue(metadata)
          ),
          objectMapper.readValue(spec, specType)
        )
      } ?: throw NoSuchResourceUID(uid)
  }

  override fun <T : Any> get(name: ResourceName, specType: Class<T>): Resource<T> {
    return jooq
      .select(UID, API_VERSION, KIND, NAME, METADATA, SPEC)
      .from(RESOURCE)
      .where(NAME.eq(name.value))
      .fetchOne()
      ?.let { (uid, apiVersion, kind, name, metadata, spec) ->
        Resource(
          ApiVersion(apiVersion),
          kind,
          ResourceMetadata(
            ResourceName(name),
            ULID.parseULID(uid),
            objectMapper.readValue(metadata)
          ),
          objectMapper.readValue(spec, specType)
        )
      } ?: throw NoSuchResourceName(name)
  }

  override fun store(resource: Resource<*>) {
    jooq.inTransaction {
      val uid = resource.metadata.uid.toString()
      val updatePairs = mapOf(
        API_VERSION to resource.apiVersion.toString(),
        KIND to resource.kind,
        NAME to resource.metadata.name.value,
        METADATA to objectMapper.writeValueAsString(resource.metadata.data),
        SPEC to objectMapper.writeValueAsString(resource.spec)
      )
      val insertPairs = updatePairs + (UID to uid)
      insertInto(
        RESOURCE,
        *insertPairs.keys.toTypedArray()
      )
        .values(insertPairs.values)
        .onDuplicateKeyUpdate()
        .set(updatePairs)
        .execute()
    }
  }

  override fun eventHistory(uid: UID): List<ResourceEvent> =
    jooq
      .select(JSON)
      .from(RESOURCE_EVENT)
      .where(UID.eq(uid.toString()))
      .orderBy(TIMESTAMP.desc())
      .fetch()
      .map { (json) ->
        objectMapper.readValue<ResourceEvent>(json)
      }
      .ifEmpty {
        throw NoSuchResourceUID(uid)
      }

  override fun appendHistory(event: ResourceEvent) {
    jooq.inTransaction {
      insertInto(RESOURCE_EVENT)
        .columns(UID, TIMESTAMP, JSON)
        .values(
          event.uid.toString(),
          event.timestamp.let(Timestamp::from),
          objectMapper.writeValueAsString(event)
        )
        .execute()
    }
  }

  override fun delete(name: ResourceName) {
    jooq.inTransaction {
      val uid = select(UID)
        .from(RESOURCE)
        .where(NAME.eq(name.value))
        .fetchOne(UID)
        ?.let(ULID::parseULID)
        ?: throw NoSuchResourceName(name)
      deleteFrom(RESOURCE)
        .where(UID.eq(uid.toString()))
        .execute()
      deleteFrom(RESOURCE_EVENT)
        .where(UID.eq(uid.toString()))
        .execute()
    }
  }

  companion object {
    private val RESOURCE = table("resource")
    private val RESOURCE_EVENT = table("resource_event")

    private val UID = field("uid", String::class.java)
    private val API_VERSION = field("api_version", String::class.java)
    private val KIND = field("kind", String::class.java)
    private val NAME = field("name", String::class.java)
    private val METADATA = field("metadata", String::class.java)
    private val SPEC = field("spec", String::class.java)
    private val JSON = field("json", String::class.java)
    private val TIMESTAMP = field("timestamp", Timestamp::class.java)
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
