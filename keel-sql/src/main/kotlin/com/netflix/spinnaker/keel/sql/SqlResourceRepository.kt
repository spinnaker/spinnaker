package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant.EPOCH

class SqlResourceRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
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
      .select(API_VERSION, KIND, METADATA, SPEC)
      .from(RESOURCE)
      .where(UID.eq(uid.toString()))
      .fetchOne()
      ?.let { (apiVersion, kind, metadata, spec) ->
        Resource(
          ApiVersion(apiVersion),
          kind,
          objectMapper.readValue(metadata),
          objectMapper.readValue(spec, specType)
        )
      } ?: throw NoSuchResourceUID(uid)
  }

  override fun <T : Any> get(name: ResourceName, specType: Class<T>): Resource<T> {
    return jooq
      .select(API_VERSION, KIND, METADATA, SPEC)
      .from(RESOURCE)
      .where(NAME.eq(name.value))
      .fetchOne()
      ?.let { (apiVersion, kind, metadata, spec) ->
        Resource(
          ApiVersion(apiVersion),
          kind,
          objectMapper.readValue(metadata),
          objectMapper.readValue(spec, specType)
        )
      } ?: throw NoSuchResourceName(name)
  }

  override fun store(resource: Resource<*>) {
    jooq.inTransaction {
      val uid = resource.uid.toString()
      val updatePairs = mapOf(
        API_VERSION to resource.apiVersion.toString(),
        KIND to resource.kind,
        NAME to resource.name.value,
        METADATA to objectMapper.writeValueAsString(resource.metadata),
        SPEC to objectMapper.writeValueAsString(resource.spec)
      )
      val insertPairs = updatePairs + (UID to uid)
      insertInto(
        RESOURCE,
        *insertPairs.keys.toTypedArray()
      )
        .values(*insertPairs.values.toTypedArray())
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

  override fun nextResourcesDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<ResourceHeader> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck).let(Timestamp::from)
    return jooq.inTransaction {
      select(UID, API_VERSION, KIND, NAME)
        .from(RESOURCE)
        .where(LAST_CHECKED.lessOrEqual(cutoff))
        .orderBy(LAST_CHECKED)
        .limit(limit)
        .forUpdate()
        .fetch()
        .also {
          it.forEach { (uid, _, _, _) ->
            update(RESOURCE)
              .set(mapOf(LAST_CHECKED to now))
              .where(UID.eq(uid))
              .execute()
          }
        }
        .map { (uid, apiVersion, kind, name) ->
          ResourceHeader(ULID.parseULID(uid), ResourceName(name), ApiVersion(apiVersion), kind)
        }
    }
  }

  override fun markCheckDue(resource: Resource<*>) {
    jooq.inTransaction {
      update(RESOURCE)
        // MySQL is stupid and won't let you insert a zero valued TIMESTAMP
        .set(mapOf(LAST_CHECKED to EPOCH.plusSeconds(1).let(Timestamp::from)))
        .where(UID.eq(resource.uid.toString()))
        .execute()
    }
  }

  companion object {
    private val RESOURCE = table("resource")
    private val RESOURCE_EVENT = table("resource_event")

    private val UID = field<String>("uid")
    private val API_VERSION = field<String>("api_version")
    private val KIND = field<String>("kind")
    private val NAME = field<String>("name")
    private val METADATA = field<String>("metadata")
    private val SPEC = field<String>("spec")
    private val LAST_CHECKED = field<Timestamp>("last_checked")
    private val JSON = field<String>("json")
    private val TIMESTAMP = field<Timestamp>("timestamp")
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
