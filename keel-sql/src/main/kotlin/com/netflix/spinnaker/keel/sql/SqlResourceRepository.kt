package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE_EVENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE_LAST_CHECKED
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH

class SqlResourceRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val objectMapper: ObjectMapper
) : ResourceRepository {

  override fun allResources(callback: (ResourceHeader) -> Unit) {
    jooq
      .select(RESOURCE.UID, RESOURCE.API_VERSION, RESOURCE.KIND, RESOURCE.NAME)
      .from(RESOURCE)
      .fetch()
      .map { (uid, apiVersion, kind, name) ->
        ResourceHeader(ULID.parseULID(uid), ResourceName(name), ApiVersion(apiVersion), kind)
      }
      .forEach(callback)
  }

  override fun <T : Any> get(uid: UID, specType: Class<T>): Resource<T> {
    return jooq
      .select(RESOURCE.API_VERSION, RESOURCE.KIND, RESOURCE.METADATA, RESOURCE.SPEC)
      .from(RESOURCE)
      .where(RESOURCE.UID.eq(uid.toString()))
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
      .select(RESOURCE.API_VERSION, RESOURCE.KIND, RESOURCE.METADATA, RESOURCE.SPEC)
      .from(RESOURCE)
      .where(RESOURCE.NAME.eq(name.value))
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

  override fun hasManagedResources(application: String): Boolean {
    return jooq
      .selectCount()
      .from(RESOURCE)
      .where(RESOURCE.APPLICATION.eq(application))
      .fetchOne()
      .value1() > 0
  }

  override fun getByApplication(application: String): List<String> {
    return jooq
      .select(RESOURCE.NAME)
      .from(RESOURCE)
      .where(RESOURCE.APPLICATION.eq(application))
      .fetch(RESOURCE.NAME)
  }

  override fun store(resource: Resource<*>) {
    val uid = resource.uid.toString()
    val updatePairs = mapOf(
      RESOURCE.API_VERSION to resource.apiVersion.toString(),
      RESOURCE.KIND to resource.kind,
      RESOURCE.NAME to resource.name.value,
      RESOURCE.METADATA to objectMapper.writeValueAsString(resource.metadata),
      RESOURCE.SPEC to objectMapper.writeValueAsString(resource.spec),
      RESOURCE.APPLICATION to resource.application
    )
    val insertPairs = updatePairs + (RESOURCE.UID to uid)
    jooq.insertInto(
      RESOURCE,
      *insertPairs.keys.toTypedArray()
    )
      .values(*insertPairs.values.toTypedArray())
      .onDuplicateKeyUpdate()
      .set(updatePairs)
      .execute()
    jooq.insertInto(RESOURCE_LAST_CHECKED)
      .set(RESOURCE_LAST_CHECKED.RESOURCE_UID, uid)
      .set(RESOURCE_LAST_CHECKED.AT, EPOCH.plusSeconds(1).toLocal())
      .onDuplicateKeyUpdate()
      .set(RESOURCE_LAST_CHECKED.AT, EPOCH.plusSeconds(1).toLocal())
      .execute()
  }

  override fun eventHistory(uid: UID): List<ResourceEvent> =
    jooq
      .select(RESOURCE_EVENT.JSON)
      .from(RESOURCE_EVENT)
      .where(RESOURCE_EVENT.UID.eq(uid.toString()))
      .orderBy(RESOURCE_EVENT.TIMESTAMP.desc())
      .fetch()
      .map { (json) ->
        objectMapper.readValue<ResourceEvent>(json)
      }
      .ifEmpty {
        throw NoSuchResourceUID(uid)
      }

  override fun appendHistory(event: ResourceEvent) {
    jooq.insertInto(RESOURCE_EVENT)
      .columns(RESOURCE_EVENT.UID, RESOURCE_EVENT.TIMESTAMP, RESOURCE_EVENT.JSON)
      .values(
        event.uid.toString(),
        event.timestamp.atZone(clock.zone).toLocalDateTime(),
        objectMapper.writeValueAsString(event)
      )
      .execute()
  }

  override fun delete(name: ResourceName) {
    val uid = jooq.select(RESOURCE.UID)
      .from(RESOURCE)
      .where(RESOURCE.NAME.eq(name.value))
      .fetchOne(RESOURCE.UID)
      ?.let(ULID::parseULID)
      ?: throw NoSuchResourceName(name)
    jooq.deleteFrom(RESOURCE)
      .where(RESOURCE.UID.eq(uid.toString()))
      .execute()
    jooq.deleteFrom(RESOURCE_EVENT)
      .where(RESOURCE_EVENT.UID.eq(uid.toString()))
      .execute()
  }

  override fun nextResourcesDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<ResourceHeader> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck).toLocal()
    return jooq.inTransaction {
      select(RESOURCE.UID, RESOURCE.API_VERSION, RESOURCE.KIND, RESOURCE.NAME)
        .from(RESOURCE, RESOURCE_LAST_CHECKED)
        .where(RESOURCE.UID.eq(RESOURCE_LAST_CHECKED.RESOURCE_UID))
        .and(RESOURCE_LAST_CHECKED.AT.lessOrEqual(cutoff))
        .orderBy(RESOURCE_LAST_CHECKED.AT)
        .limit(limit)
        .forUpdate()
        .fetch()
        .also {
          it.forEach { (uid, _, _, _) ->
            insertInto(RESOURCE_LAST_CHECKED)
              .set(RESOURCE_LAST_CHECKED.RESOURCE_UID, uid)
              .set(RESOURCE_LAST_CHECKED.AT, now.toLocal())
              .onDuplicateKeyUpdate()
              .set(RESOURCE_LAST_CHECKED.AT, now.toLocal())
              .execute()
          }
        }
        .map { (uid, apiVersion, kind, name) ->
          ResourceHeader(ULID.parseULID(uid), ResourceName(name), ApiVersion(apiVersion), kind)
        }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun Instant.toLocal() = atZone(clock.zone).toLocalDateTime()
}
