package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.events.PersistentEvent
import com.netflix.spinnaker.keel.events.PersistentEvent.EventScope
import com.netflix.spinnaker.keel.events.ResourceCheckResult
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceHistoryEvent
import com.netflix.spinnaker.keel.events.ResourceState
import com.netflix.spinnaker.keel.pause.PauseScope
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTIVE_ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTIVE_RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DIFF_FINGERPRINT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.EVENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.PAUSED
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE_LAST_CHECKED
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE_VERSION
import com.netflix.spinnaker.keel.resources.ResourceFactory
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import com.netflix.spinnaker.keel.telemetry.AboutToBeChecked
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL
import org.jooq.impl.DSL.coalesce
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.value
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH

open class SqlResourceRepository(
  private val jooq: DSLContext,
  override val clock: Clock,
  private val objectMapper: ObjectMapper,
  private val resourceFactory: ResourceFactory,
  private val sqlRetry: SqlRetry,
  private val publisher: ApplicationEventPublisher,
  private val spectator: Registry,
  private val springEnv: Environment
) : ResourceRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Insert into RESOURCE_VERSION table is frequently deadlocking.
   * Creating a metric to get insight into affected apps
   */
  private val resourceVersionInsertId = spectator.createId("resource.version.insert")

  private val itemsDueForCheckCheckSingleSelectQuery : Boolean
    get() = springEnv.getProperty("keel.items.due.for.check.single.select.query", Boolean::class.java, false)

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
      resourceFactory.create(kind, metadata, spec)
    }

  override fun getRaw(id: String): Resource<ResourceSpec> =
    readResource(id) { kind, metadata, spec ->
      resourceFactory.createRaw(kind, metadata, spec)
    }

  private fun readResource(id: String, callback: (String, String, String) -> Resource<ResourceSpec>): Resource<ResourceSpec> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(ACTIVE_RESOURCE.KIND, ACTIVE_RESOURCE.METADATA, ACTIVE_RESOURCE.SPEC)
        .from(ACTIVE_RESOURCE)
        .where(ACTIVE_RESOURCE.ID.eq(id))
        .fetchOne()
        ?.let { (kind, metadata, spec) ->
          callback(kind, metadata, spec)
        } ?: throw NoSuchResourceId(id)
    }

  override fun getResourcesByApplication(application: String): List<Resource<*>> {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(ACTIVE_RESOURCE.KIND, ACTIVE_RESOURCE.METADATA, ACTIVE_RESOURCE.SPEC)
        .from(ACTIVE_RESOURCE)
        .where(ACTIVE_RESOURCE.APPLICATION.eq(application))
        .fetch()
        .map { (kind, metadata, spec) ->
          resourceFactory.create(kind, metadata, spec)
        }
    }
  }

  override fun hasManagedResources(application: String): Boolean =
    sqlRetry.withRetry(READ) {
      jooq
        .selectCount()
        .from(RESOURCE)
        .where(RESOURCE.APPLICATION.eq(application))
        .fetchSingle()
        .value1() > 0
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
  override fun <T : ResourceSpec> store(resource: Resource<T>): Resource<T> {
    val version = jooq.select(
        coalesce(
          max(RESOURCE_VERSION.VERSION),
          value(0)
        )
      )
      .from(RESOURCE_VERSION)
      .join(RESOURCE)
      .on(RESOURCE.UID.eq(RESOURCE_VERSION.RESOURCE_UID))
      .where(RESOURCE.ID.eq(resource.id))
      .fetchSingleInto<Int>()

    val uid = if (version > 0 ) {
      getResourceUid(resource.id)
    } else {
      randomUID().toString()
        .also { uid ->
          jooq.insertInto(RESOURCE)
            .set(RESOURCE.UID, uid)
            .set(RESOURCE.KIND, resource.kind.toString())
            .set(RESOURCE.ID, resource.id)
            .set(RESOURCE.APPLICATION, resource.application)
            .execute()
        }
    }

    try {
      jooq.insertInto(RESOURCE_VERSION)
        .set(RESOURCE_VERSION.RESOURCE_UID, uid)
        .set(RESOURCE_VERSION.VERSION, version + 1)
        .set(RESOURCE_VERSION.SPEC, objectMapper.writeValueAsString(resource.spec))
        .set(RESOURCE_VERSION.CREATED_AT, clock.instant())
        .execute()
    } catch(e: Exception) {
      log.error("Failed to insert resource version for ${resource.id}: $e", e)
      spectator.counter(resourceVersionInsertId.withTags(
        "success", "false",
        "application", resource.application, // Capture the app on fail cases to help repro
        "kind", resource.kind.toString()
      ))
        .increment()
      throw e
    }

    spectator.counter(resourceVersionInsertId.withTag("success", "true")).increment()

    jooq.insertInto(RESOURCE_LAST_CHECKED)
      .set(RESOURCE_LAST_CHECKED.RESOURCE_UID, uid)
      .set(RESOURCE_LAST_CHECKED.AT, EPOCH.plusSeconds(1))
      .onDuplicateKeyUpdate()
      .set(RESOURCE_LAST_CHECKED.AT, EPOCH.plusSeconds(1))
      .execute()

    return resource.copy(
      metadata = resource.metadata + mapOf("uid" to uid, "version" to version + 1)
    )
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

    return sqlRetry.withRetry(READ) {
      jooq
        .select(EVENT.JSON)
        .from(EVENT)
        // look for resource events that match the resource...
        .where(
          EVENT.SCOPE.eq(EventScope.RESOURCE)
            .and(EVENT.REF.eq(id))
        )
        // ...or application events that match the application as they apply to all resources
        .or(
          EVENT.SCOPE.eq(EventScope.APPLICATION)
            .and(EVENT.APPLICATION.eq(applicationForId(id)))
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

    if (event.ignoreRepeatedInHistory) {
      val previousEvent = sqlRetry.withRetry(READ) {
        jooq
          .select(EVENT.JSON)
          .from(EVENT)
          // look for resource events that match the resource...
          .where(
            EVENT.SCOPE.eq(EventScope.RESOURCE)
              .and(EVENT.REF.eq(event.ref))
          )
          // ...or application events that match the application as they apply to all resources
          .or(
            EVENT.SCOPE.eq(EventScope.APPLICATION)
              .and(EVENT.APPLICATION.eq(event.application))
          )
          .orderBy(EVENT.TIMESTAMP.desc())
          .limit(1)
          .fetchOne(EVENT.JSON)
      }

      if (event.javaClass == previousEvent?.javaClass) return
    }

    sqlRetry.withRetry(WRITE) {
      jooq
        .insertInto(EVENT)
        .set(EVENT.UID, ULID().nextULID(event.timestamp.toEpochMilli()))
        .set(EVENT.SCOPE, event.scope)
        .set(EVENT.JSON, event)
        .execute()
    }
  }

  override fun delete(id: String) {
    // TODO: these should be run inside a transaction
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(RESOURCE)
        .where(RESOURCE.ID.eq(id))
        .execute()
        .also { count ->
          if (count == 0) {
            throw NoSuchResourceId(id)
          }
        }
    }
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(EVENT)
        .where(EVENT.SCOPE.eq(EventScope.RESOURCE))
        .and(EVENT.REF.eq(id))
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

  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<Resource<ResourceSpec>> =
    when (itemsDueForCheckCheckSingleSelectQuery) {
      true -> itemsDueForCheckSingleSelectQuery(minTimeSinceLastCheck, limit)
      false -> itemsDueForCheckMultipleQueries(minTimeSinceLastCheck, limit)
    }

  /**
   * This implementation uses a single select query.
   *
   * This is the original implementation, but we often got deadlocks when people would update a delivery config,
   * on inserting a record into the resource_version table, which is one of the tables that backs the active_resource
   * view.
   */
  private fun itemsDueForCheckSingleSelectQuery(minTimeSinceLastCheck: Duration, limit: Int): Collection<Resource<ResourceSpec>> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck)
    return sqlRetry.withRetry(WRITE) {
      jooq.inTransaction {
        select(
          ACTIVE_RESOURCE.UID,
          ACTIVE_RESOURCE.KIND,
          ACTIVE_RESOURCE.METADATA,
          ACTIVE_RESOURCE.SPEC,
          ACTIVE_RESOURCE.APPLICATION,
          RESOURCE_LAST_CHECKED.AT
        )
          .from(ACTIVE_RESOURCE, RESOURCE_LAST_CHECKED)
          .where(ACTIVE_RESOURCE.UID.eq(RESOURCE_LAST_CHECKED.RESOURCE_UID))
          .and(RESOURCE_LAST_CHECKED.AT.lessOrEqual(cutoff))
          .and(RESOURCE_LAST_CHECKED.IGNORE.notEqual(true))
          .orderBy(RESOURCE_LAST_CHECKED.AT)
          .limit(limit)
          .forUpdate()
          .fetch()
          .also {
            it.forEach { (uid, _, _, _, application, lastCheckedAt) ->
              insertInto(RESOURCE_LAST_CHECKED)
                .set(RESOURCE_LAST_CHECKED.RESOURCE_UID, uid)
                .set(RESOURCE_LAST_CHECKED.AT, now)
                .onDuplicateKeyUpdate()
                .set(RESOURCE_LAST_CHECKED.AT, now)
                .execute()
              publisher.publishEvent(AboutToBeChecked(
                lastCheckedAt,
                "resource",
                "application:$application"
              ))
            }
          }
      }
        .map { (uid, kind, metadata, spec) ->
          try {
            resourceFactory.create(kind, metadata, spec)
          } catch (e: Exception) {
            jooq.insertInto(RESOURCE_LAST_CHECKED)
              .set(RESOURCE_LAST_CHECKED.RESOURCE_UID, uid)
              .set(RESOURCE_LAST_CHECKED.AT, now)
              .set(RESOURCE_LAST_CHECKED.STATUS, ResourceState.Error)
              .set(RESOURCE_LAST_CHECKED.IGNORE, true)
              .onDuplicateKeyUpdate()
              .set(RESOURCE_LAST_CHECKED.AT, now)
              .set(RESOURCE_LAST_CHECKED.STATUS, ResourceState.Error)
              .set(RESOURCE_LAST_CHECKED.IGNORE, true)
              .execute()
            throw e
          }
        }
    }
  }

  /**
   * This implementation uses two select queries.
   *
   * The first query only runs against the resource_last_checked table. This is to reduce the scope of the FOR UPDATE
   * lock.
   *
   */
  private fun itemsDueForCheckMultipleQueries(minTimeSinceLastCheck: Duration, limit: Int): Collection<Resource<ResourceSpec>> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck)
    return sqlRetry.withRetry(WRITE) {

      /**
       * Ideally, we would use a single UPDATE ... RETURNING query instead of
       * separate SELECT/UPDATE queries in a transaction
       * Unfortunately, MySQL doesn't support RETURNING and jOOQ doesn't emulate it.
       * see: https://github.com/jOOQ/jOOQ/issues/6865
       */
      val resourcesToCheck = jooq.inTransaction {
        select(RESOURCE_LAST_CHECKED.RESOURCE_UID, RESOURCE_LAST_CHECKED.AT)
          .from(RESOURCE_LAST_CHECKED)
          .where(RESOURCE_LAST_CHECKED.AT.lessOrEqual(cutoff))
          .and(RESOURCE_LAST_CHECKED.IGNORE.notEqual(true))
          .orderBy(RESOURCE_LAST_CHECKED.AT)
          .limit(limit)
          .forUpdate()
          .fetch()
          .map {(uid, at) -> uid to at }
          .toMap()
          .also { m ->
            update(RESOURCE_LAST_CHECKED)
              .set(RESOURCE_LAST_CHECKED.AT, now)
              .where(RESOURCE_LAST_CHECKED.RESOURCE_UID.`in`(m.keys))
              .execute()
          }
      }

      jooq.select(
        ACTIVE_RESOURCE.UID,
        ACTIVE_RESOURCE.KIND,
        ACTIVE_RESOURCE.METADATA,
        ACTIVE_RESOURCE.SPEC,
        ACTIVE_RESOURCE.APPLICATION
      )
        .from(ACTIVE_RESOURCE)
        .where(ACTIVE_RESOURCE.UID.`in`(resourcesToCheck.keys))
        .fetch()
        .map { (uid, kind, metadata, spec, application) ->
          try {
            publisher.publishEvent(
              AboutToBeChecked(
                resourcesToCheck[uid]!!,
                "resource",
                "application:$application"
              )
            )

            resourceFactory.create(kind, metadata, spec)

          } catch (e: Exception) {
            jooq.insertInto(RESOURCE_LAST_CHECKED)
              .set(RESOURCE_LAST_CHECKED.RESOURCE_UID, uid)
              .set(RESOURCE_LAST_CHECKED.AT, now)
              .set(RESOURCE_LAST_CHECKED.STATUS, ResourceState.Error)
              .set(RESOURCE_LAST_CHECKED.IGNORE, true)
              .onDuplicateKeyUpdate()
              .set(RESOURCE_LAST_CHECKED.AT, now)
              .set(RESOURCE_LAST_CHECKED.STATUS, ResourceState.Error)
              .set(RESOURCE_LAST_CHECKED.IGNORE, true)
              .execute()
            throw e
          }
        }
    }
  }

  override fun markCheckComplete(resource: Resource<*>, status: Any?) {
    require(status is ResourceState)
    sqlRetry.withRetry(WRITE) {
      val now = clock.instant()
      jooq
        .insertInto(RESOURCE_LAST_CHECKED)
        .set(RESOURCE_LAST_CHECKED.RESOURCE_UID, resource.uid)
        .set(RESOURCE_LAST_CHECKED.STATUS, status)
        .set(RESOURCE_LAST_CHECKED.STATUS_DETERMINED_AT, now)
        .onDuplicateKeyUpdate()
        .set(RESOURCE_LAST_CHECKED.STATUS, status)
        .set(RESOURCE_LAST_CHECKED.STATUS_DETERMINED_AT, now)
        .execute()
    }
  }

  @EventListener(ResourceCheckResult::class)
  fun onResourceChecked(event: ResourceCheckResult) {
    markCheckComplete(get(event.id), event.state)
  }

  override fun triggerResourceRecheck(environmentName: String, application: String) {
    log.debug("Triggering recheck for environment $environmentName in application $application")
    sqlRetry.withRetry(WRITE) {
      jooq.transaction { config ->
        val txn = DSL.using(config)
        val resourceUids =
          txn.select(ENVIRONMENT_RESOURCE.RESOURCE_UID)
            .from(ENVIRONMENT_RESOURCE)
            .innerJoin(ACTIVE_ENVIRONMENT)
            .on(ACTIVE_ENVIRONMENT.UID.eq(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID))
            .innerJoin(DELIVERY_CONFIG)
            .on(ACTIVE_ENVIRONMENT.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))
            .where(ACTIVE_ENVIRONMENT.NAME.eq(environmentName))
            .and(DELIVERY_CONFIG.APPLICATION.eq(application))
            .fetch()

        log.debug("Triggering recheck for resources $resourceUids in environment $environmentName in application $application")

        txn.update(RESOURCE_LAST_CHECKED)
          .set(RESOURCE_LAST_CHECKED.AT, EPOCH.plusSeconds(1))
          .where(RESOURCE_LAST_CHECKED.RESOURCE_UID.`in`(resourceUids))
          .execute()
      }
    }
  }

  override fun incrementDeletionAttempts(resource: Resource<*>) {
    sqlRetry.withRetry(WRITE) {
      jooq.update(RESOURCE)
        .set(RESOURCE.ATTEMPTED_DELETIONS, RESOURCE.ATTEMPTED_DELETIONS + 1)
        .where(RESOURCE.ID.eq(resource.id))
        .execute()
    }
  }

  override fun countDeletionAttempts(resource: Resource<*>): Int {
    return sqlRetry.withRetry(READ) {
      jooq.select(RESOURCE.ATTEMPTED_DELETIONS)
        .from(RESOURCE)
        .where(RESOURCE.ID.eq(resource.id))
        .fetchOne(RESOURCE.ATTEMPTED_DELETIONS)!!
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

  private fun applicationForId(id: String): Select<Record1<String>> =
    select(RESOURCE.APPLICATION)
      .from(RESOURCE)
      .where(RESOURCE.ID.eq(id))
      .limit(1)

  private val Resource<*>.uid: String
    get() = getResourceUid(id)
}
