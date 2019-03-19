package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceState
import com.netflix.spinnaker.keel.persistence.ResourceState.Unknown
import com.netflix.spinnaker.keel.persistence.ResourceState.valueOf
import com.netflix.spinnaker.keel.persistence.ResourceStateHistoryEntry
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
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
  private val clock: Clock,
  private val instanceIdSupplier: InstanceIdSupplier
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

  override fun <T : Any> get(uid: UID, specType: Class<T>): Resource<T> {
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
      val uid = resource.metadata.uid.toString()
      val updatePairs = mapOf(
        field("api_version") to resource.apiVersion.toString(),
        field("kind") to resource.kind,
        field("name") to resource.metadata.name.value,
        field("resource_version") to resource.metadata.resourceVersion,
        field("spec") to objectMapper.writeValueAsString(resource.spec)
      )
      val insertPairs = updatePairs + (field("uid") to uid)
      insertInto(
        RESOURCE,
        *insertPairs.keys.toTypedArray()
      )
        .values(insertPairs.values)
        .onDuplicateKeyUpdate()
        .set(updatePairs)
        .execute()

      insertInto(RESOURCE_STATE)
        .columns(
          field("uid"),
          field("state"),
          field("timestamp"),
          field("instance_id")
        )
        .values(
          resource.metadata.uid.toString(),
          Unknown.name,
          clock.instant().let(Timestamp::from),
          instanceIdSupplier.get()
        )
        .execute()
    }
  }

  override fun lastKnownState(uid: UID): ResourceStateHistoryEntry =
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
          ResourceStateHistoryEntry(state, timestamp)
        } else {
          throw NoSuchResourceUID(uid)
        }
      }

  override fun stateHistory(uid: UID): List<ResourceStateHistoryEntry> =
    jooq
      .select(
        field("state"),
        field("timestamp")
      )
      .from(RESOURCE_STATE)
      .where(field("uid").eq(uid.toString()))
      .orderBy(field("timestamp").desc())
      .fetch()
      .intoResultSet()
      .run {
        val results = mutableListOf<ResourceStateHistoryEntry>()
        while (next()) {
          results.add(ResourceStateHistoryEntry(state, timestamp))
        }
        results
      }
      .apply {
        if (isEmpty()) throw NoSuchResourceUID(uid)
      }

  override fun updateState(uid: UID, state: ResourceState) {
    // TODO: long term it may make more sense to use 2 tables
    // one storing the "latest" state and one storing the full
    // history. Can then do a single tx.
    jooq.inTransaction {
      select(
        field("state")
      )
        .from(RESOURCE_STATE)
        .where(
          field("uid").eq(uid.toString())
        )
        .orderBy(field("timestamp").desc())
        .limit(1)
        .forUpdate()
        .fetch()
        .intoResultSet()
        .apply {
          if (!next() || getString("state") != state.name) {
            insertInto(RESOURCE_STATE)
              .columns(
                field("uid"),
                field("state"),
                field("timestamp"),
                field("instance_id")
              )
              .values(
                uid.toString(),
                state.name,
                clock.instant().let(Timestamp::from),
                instanceIdSupplier.get()
              )
              .execute()
          }
        }
    }
  }

  override fun delete(name: ResourceName) {
    jooq.inTransaction {
      deleteFrom(RESOURCE)
        .where(field("name").eq(name.value))
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
  private val ResultSet.uid: UID
    get() = getString("uid").let(ULID::parseULID)

  private fun <T : Any> ResultSet.spec(type: Class<T>): T =
    objectMapper.readValue(getString("spec"), type)

  private val ResultSet.state: ResourceState
    get() = getString("state").let { valueOf(it) }
  private val ResultSet.timestamp: Instant
    get() = getTimestamp("timestamp").toInstant()

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
