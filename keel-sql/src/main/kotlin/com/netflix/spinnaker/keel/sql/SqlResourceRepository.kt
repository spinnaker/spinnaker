package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceState
import com.netflix.spinnaker.keel.persistence.ResourceState.Unknown
import com.netflix.spinnaker.keel.persistence.ResourceState.valueOf
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.using
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant

class SqlResourceRepository(
  private val jooq: DSLContext,
  private val objectMapper: ObjectMapper,
  private val clock: Clock
) : ResourceRepository {

  override fun allResources(callback: (ResourceHeader) -> Unit) {
    jooq
      .select(
        field("uid"),
        field("api_version"),
        field("kind"),
        field("name"),
        field("resource_version")
      )
      .from(RESOURCE)
      .fetch()
      .intoResultSet()
      .apply {
        while (next()) {
          callback(
            ResourceHeader(
              uid = uid,
              name = resourceName,
              resourceVersion = resourceVersion,
              apiVersion = apiVersion,
              kind = kind
            )
          )
        }
      }
  }

  override fun <T : Any> get(uid: ULID.Value, specType: Class<T>): Resource<T> {
    return jooq
      .select(
        field("uid"),
        field("api_version"),
        field("kind"),
        field("name"),
        field("resource_version"),
        field("spec")
      )
      .from(RESOURCE)
      .where(field("uid").eq(uid.toString()))
      .fetch()
      .intoResultSet()
      .run {
        if (next()) {
          toResource(specType)
        } else {
          throw NoSuchResourceUID(uid)
        }
      }
  }

  override fun <T : Any> get(name: ResourceName, specType: Class<T>): Resource<T> {
    return jooq
      .select(
        field("uid"),
        field("api_version"),
        field("kind"),
        field("name"),
        field("resource_version"),
        field("spec")
      )
      .from(RESOURCE)
      .where(field("name").eq(name.value))
      .fetch()
      .intoResultSet()
      .run {
        if (next()) {
          toResource(specType)
        } else {
          throw NoSuchResourceName(name)
        }
      }
  }

  override fun store(resource: Resource<*>) {
    jooq.inTransaction {
      val uid = resource.metadata.uid?.toString()
      val updatePairs = mapOf(
        field("api_version") to resource.apiVersion.toString(),
        field("kind") to resource.kind,
        field("name") to resource.metadata.name.value,
        field("resource_version") to resource.metadata.resourceVersion,
        field("spec") to objectMapper.writeValueAsString(resource.spec)
      )
      val insertPairs = updatePairs + (field("uid") to uid)
      try {
        insertInto(
          RESOURCE,
          *insertPairs.keys.toTypedArray()
        )
          .values(insertPairs.values)
          .onDuplicateKeyUpdate()
          .set(updatePairs)
          .execute()
      } catch (e: SQLDialectNotSupportedException) {
        log.warn("Falling back to primitive upsert logic: ${e.message}")
        val exists = fetchExists(
          select()
            .from(RESOURCE)
            .where(field("uid").eq(uid))
        )
        if (exists) {
          update(RESOURCE)
            .set(updatePairs)
            .where(field("uid").eq(uid))
            .execute()
        } else {
          insertInto(RESOURCE)
            .columns(insertPairs.keys)
            .values(insertPairs.values)
            .execute()
        }
      }

      insertInto(RESOURCE_STATE)
        .columns(
          field("uid"),
          field("state"),
          field("timestamp")
        )
        .values(
          resource.metadata.uid.toString(),
          Unknown.name,
          clock.instant().let(Timestamp::from)
        )
        .execute()
    }
  }

  override fun lastKnownState(uid: ULID.Value): Pair<ResourceState, Instant> =
    jooq
      .select(
        field("state"),
        field("timestamp")
      )
      .from(RESOURCE_STATE)
      .where(field("uid").eq(uid.toString()))
      .orderBy(field("timestamp").desc())
      .limit(1)
      .fetch()
      .intoResultSet()
      .run {
        if (next()) {
          state to timestamp
        } else {
          throw IllegalStateException("No state found for resource $uid")
        }
      }

  override fun updateState(uid: ULID.Value, state: ResourceState) {
    jooq.inTransaction {
      insertInto(RESOURCE_STATE)
        .columns(
          field("uid"),
          field("state"),
          field("timestamp")
        )
        .values(
          uid.toString(),
          state.name,
          clock.instant().let(Timestamp::from)
        )
        .execute()
    }
  }

  override fun delete(uid: ULID.Value) {
    jooq.inTransaction {
      deleteFrom(RESOURCE)
        .where(field("uid").eq(uid.toString()))
        .execute()
    }
  }

  companion object {
    private val RESOURCE = DSL.table("resource")
    private val RESOURCE_STATE = DSL.table("resource_state")
  }

  private fun DSLContext.inTransaction(fn: DSLContext.() -> Unit) {
    transaction { tx ->
      using(tx).apply(fn)
    }
  }

  private fun <T : Any> ResultSet.toResource(specType: Class<T>): Resource<T> =
    Resource(
      apiVersion,
      kind,
      metadata,
      spec(specType)
    )

  private val ResultSet.metadata: ResourceMetadata
    get() = ResourceMetadata(name = resourceName, uid = uid, resourceVersion = resourceVersion)
  private val ResultSet.resourceName: ResourceName
    get() = getString("name").let(::ResourceName)
  private val ResultSet.resourceVersion: Long
    get() = getLong("resource_version")
  private val ResultSet.apiVersion: ApiVersion
    get() = getString("api_version").let(::ApiVersion)
  private val ResultSet.kind: String
    get() = getString("kind")
  private val ResultSet.uid: ULID.Value
    get() = getString("uid").let(ULID::parseULID)

  private fun <T : Any> ResultSet.spec(type: Class<T>): T =
    objectMapper.readValue(getString("spec"), type)

  private val ResultSet.state: ResourceState
    get() = getString("state").let { valueOf(it) }
  private val ResultSet.timestamp: Instant
    get() = getTimestamp("timestamp").toInstant()

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
