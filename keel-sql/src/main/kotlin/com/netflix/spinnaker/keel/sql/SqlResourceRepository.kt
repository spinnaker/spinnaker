package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.events.PersistentEvent
import com.netflix.spinnaker.keel.events.PersistentEvent.EventScope
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceHistoryEvent
import com.netflix.spinnaker.keel.pause.PauseScope
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DIFF_FINGERPRINT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.EVENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.PAUSED
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE_LAST_CHECKED
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE_WITH_METADATA
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH

open class SqlResourceRepository(
  private val jooq: DSLContext,
  override val clock: Clock,
  private val resourceSpecIdentifier: ResourceSpecIdentifier,
  private val specMigrators: List<SpecMigrator<*, *>>,
  private val objectMapper: ObjectMapper,
  private val sqlRetry: SqlRetry
) : ResourceRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val resourceFactory = ResourceFactory(objectMapper, resourceSpecIdentifier, specMigrators)

  override fun allResources(callback: (ResourceHeader) -> Unit) {
    sqlRetry.withRetry(READ) {
      jooq
        .select(RESOURCE.KIND, RESOURCE.ID)
        .from(RESOURCE)
        .fetch()
        .map { (kind, id) ->
          ResourceHeader(id, parseKind(kind))
        }
        .forEach(callback)
    }
  }

  override fun get(id: String): Resource<ResourceSpec> =
    readResource(id) { kind, metadata, spec ->
      resourceFactory.invoke(kind, metadata, spec)
    }

  override fun getRaw(id: String): Resource<ResourceSpec> =
    readResource(id) { kind, metadata, spec ->
      parseKind(kind).let {
        Resource(
          it,
          objectMapper.readValue<Map<String, Any?>>(metadata).asResourceMetadata(),
          objectMapper.readValue(spec, resourceSpecIdentifier.identify(it))
        )
      }
    }

  private fun readResource(id: String, callback: (String, String, String) -> Resource<ResourceSpec>): Resource<ResourceSpec> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(RESOURCE_WITH_METADATA.KIND, RESOURCE_WITH_METADATA.METADATA, RESOURCE_WITH_METADATA.SPEC)
        .from(RESOURCE_WITH_METADATA)
        .where(RESOURCE_WITH_METADATA.ID.eq(id))
        .fetchOne()
        ?.let { (kind, metadata, spec) ->
          callback(kind, metadata, spec)
        } ?: throw NoSuchResourceId(id)
    }

  override fun getResourcesByApplication(application: String): List<Resource<*>> {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(RESOURCE_WITH_METADATA.KIND, RESOURCE_WITH_METADATA.METADATA, RESOURCE_WITH_METADATA.SPEC)
        .from(RESOURCE_WITH_METADATA)
        .where(RESOURCE_WITH_METADATA.APPLICATION.eq(application))
        .fetch()
        .map { (kind, metadata, spec) ->
          resourceFactory.invoke(kind, metadata, spec)
        }
    }
  }

  override fun hasManagedResources(application: String): Boolean {
    return sqlRetry.withRetry(READ) {
      jooq
        .selectCount()
        .from(RESOURCE)
        .where(RESOURCE.APPLICATION.eq(application))
        .fetchOne()
        .value1() > 0
    }
  }

  override fun getResourceIdsByApplication(application: String): List<String> {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(RESOURCE.ID)
        .from(RESOURCE)
        .where(RESOURCE.APPLICATION.eq(application))
        .fetch(RESOURCE.ID)
    }
  }

  // todo: this is not retryable due to overall repository structure: https://github.com/spinnaker/keel/issues/740
  override fun store(resource: Resource<*>) {
    val uid = jooq.select(RESOURCE.UID)
      .from(RESOURCE)
      .where(RESOURCE.ID.eq(resource.id))
      .fetchOne(RESOURCE.UID)
      ?: randomUID().toString()

    val updatePairs = mapOf(
      RESOURCE.KIND to resource.kind,
      RESOURCE.ID to resource.id,
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
      .set(RESOURCE_LAST_CHECKED.AT, EPOCH.plusSeconds(1))
      .onDuplicateKeyUpdate()
      .set(RESOURCE_LAST_CHECKED.AT, EPOCH.plusSeconds(1))
      .set(RESOURCE_LAST_CHECKED.IGNORE, false)
      .execute()
  }

  override fun applicationEventHistory(application: String, limit: Int): List<ApplicationEvent> {
    require(limit > 0) { "limit must be a positive integer" }
    return sqlRetry.withRetry(READ) {
      jooq
        .select(EVENT.JSON)
        .from(EVENT)
        .where(EVENT.SCOPE.eq(EventScope.APPLICATION))
        .and(EVENT.REF.eq(application))
        .orderBy(EVENT.TIMESTAMP.desc())
        .limit(limit)
        .fetch(EVENT.JSON)
        .filterIsInstance<ApplicationEvent>()
    }
  }

  override fun applicationEventHistory(application: String, after: Instant): List<ApplicationEvent> {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(EVENT.JSON)
        .from(EVENT)
        .where(EVENT.SCOPE.eq(EventScope.APPLICATION))
        .and(EVENT.REF.eq(application))
        .and(EVENT.TIMESTAMP.greaterOrEqual(after))
        .orderBy(EVENT.TIMESTAMP.desc())
        .fetch(EVENT.JSON)
        .filterIsInstance<ApplicationEvent>()
    }
  }

  override fun eventHistory(id: String, limit: Int): List<ResourceHistoryEvent> {
    require(limit > 0) { "limit must be a positive integer" }

    val resource = get(id)

    return sqlRetry.withRetry(READ) {
      jooq
        .select(EVENT.JSON)
        .from(EVENT)
        // look for resource events that match the resource...
        .where(
          EVENT.SCOPE.eq(EventScope.RESOURCE)
            .and(EVENT.REF.eq(resource.uid))
        )
        // ...or application events that match the application as they apply to all resources
        .or(
          EVENT.SCOPE.eq(EventScope.APPLICATION)
            .and(EVENT.APPLICATION.eq(resource.application))
        )
        .orderBy(EVENT.TIMESTAMP.desc())
        .limit(limit)
        .fetch(EVENT.JSON)
        // filter out application events that don't affect resource history
        .filterIsInstance<ResourceHistoryEvent>()
    }
  }

  // todo: add sql retries once we've rethought repository structure: https://github.com/spinnaker/keel/issues/740
  override fun appendHistory(event: ResourceEvent) {
    // for historical reasons, we use the resource UID (not the ID) as an identifier in resource events
    val ref = getResourceUid(event.ref)
    doAppendHistory(event, ref)
  }

  override fun appendHistory(event: ApplicationEvent) {
    doAppendHistory(event, event.application)
  }

  private fun doAppendHistory(event: PersistentEvent, ref: String) {
    log.debug("Appending event: $event")
    jooq.transaction { config ->
      val txn = DSL.using(config)

      if (event.ignoreRepeatedInHistory) {
        val previousEvent = txn
          .select(EVENT.JSON)
          .from(EVENT)
          // look for resource events that match the resource...
          .where(
            EVENT.SCOPE.eq(EventScope.RESOURCE)
              .and(EVENT.REF.eq(ref))
          )
          // ...or application events that match the application as they apply to all resources
          .or(
            EVENT.SCOPE.eq(EventScope.APPLICATION)
              .and(EVENT.APPLICATION.eq(event.application))
          )
          .orderBy(EVENT.TIMESTAMP.desc())
          .limit(1)
          .fetchOne(EVENT.JSON)

        if (event.javaClass == previousEvent?.javaClass) return@transaction
      }

      txn
        .insertInto(EVENT)
        .set(EVENT.UID, ULID().nextULID(event.timestamp.toEpochMilli()))
        .set(EVENT.SCOPE, event.scope)
        .set(EVENT.REF, ref)
        .set(EVENT.TIMESTAMP, event.timestamp)
        .set(EVENT.JSON, event)
        .execute()
    }
  }

  override fun delete(id: String) {
    // TODO: these should be run inside a transaction
    val uid = sqlRetry.withRetry(READ) {
      jooq.select(RESOURCE.UID)
        .from(RESOURCE)
        .where(RESOURCE.ID.eq(id))
        .fetchOne(RESOURCE.UID)
        ?.let(ULID::parseULID)
        ?: throw NoSuchResourceId(id)
    }
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(RESOURCE)
        .where(RESOURCE.UID.eq(uid.toString()))
        .execute()
    }
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(EVENT)
        .where(EVENT.SCOPE.eq(EventScope.RESOURCE))
        .and(EVENT.REF.eq(uid.toString()))
        .execute()
    }
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(DIFF_FINGERPRINT)
        .where(DIFF_FINGERPRINT.ENTITY_ID.eq(id))
        .execute()
    }
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(PAUSED)
        .where(PAUSED.SCOPE.eq(PauseScope.RESOURCE))
        .and(PAUSED.NAME.eq(id))
        .execute()
    }
  }

  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<Resource<ResourceSpec>> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck)
    return sqlRetry.withRetry(WRITE) {
      jooq.inTransaction {
        select(RESOURCE_WITH_METADATA.UID, RESOURCE_WITH_METADATA.KIND, RESOURCE_WITH_METADATA.METADATA, RESOURCE_WITH_METADATA.SPEC)
          .from(RESOURCE_WITH_METADATA, RESOURCE_LAST_CHECKED)
          .where(RESOURCE_WITH_METADATA.UID.eq(RESOURCE_LAST_CHECKED.RESOURCE_UID))
          .and(RESOURCE_LAST_CHECKED.AT.lessOrEqual(cutoff))
          .and(RESOURCE_LAST_CHECKED.IGNORE.notEqual(true))
          .orderBy(RESOURCE_LAST_CHECKED.AT)
          .limit(limit)
          .forUpdate()
          .fetch()
          .also {
            it.forEach { (uid, _, _, _) ->
              insertInto(RESOURCE_LAST_CHECKED)
                .set(RESOURCE_LAST_CHECKED.RESOURCE_UID, uid)
                .set(RESOURCE_LAST_CHECKED.AT, now)
                .onDuplicateKeyUpdate()
                .set(RESOURCE_LAST_CHECKED.AT, now)
                .execute()
            }
          }
      }
        .map { (uid, kind, metadata, spec) ->
          try {
            resourceFactory.invoke(kind, metadata, spec)
          } catch (e: Exception) {
            jooq.insertInto(RESOURCE_LAST_CHECKED)
              .set(RESOURCE_LAST_CHECKED.RESOURCE_UID, uid)
              .set(RESOURCE_LAST_CHECKED.IGNORE, true)
              .onDuplicateKeyUpdate()
              .set(RESOURCE_LAST_CHECKED.IGNORE, true)
              .execute()
            throw e
          }
        }
    }
  }

  fun getResourceUid(id: String) =
    sqlRetry.withRetry(READ) {
      jooq
        .select(RESOURCE.UID)
        .from(RESOURCE)
        .where(RESOURCE.ID.eq(id))
        .fetchOne(RESOURCE.UID)
        ?: throw IllegalStateException("Resource with id $id not found. Retrying.")
    }

  private val Resource<*>.uid: String
    get() = getResourceUid(this.id)
}
