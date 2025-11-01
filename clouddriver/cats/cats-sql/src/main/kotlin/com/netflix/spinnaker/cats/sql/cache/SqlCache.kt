package com.netflix.spinnaker.cats.sql.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultJsonCacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.provider.ProviderCacheConfiguration
import com.netflix.spinnaker.cats.sql.SqlUtil
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND
import com.netflix.spinnaker.config.SqlConstraints
import com.netflix.spinnaker.config.coroutineThreadPrefix
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import de.huxhorn.sulky.ulid.ULID
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.vavr.control.Try
import java.security.MessageDigest
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.SQLSyntaxErrorException
import java.time.Clock
import java.time.Duration
import java.util.Arrays
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger
import jakarta.annotation.PreDestroy
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.noCondition
import org.jooq.impl.DSL.sql
import org.jooq.impl.DSL.table
import org.jooq.util.mysql.MySQLDSL
import org.slf4j.LoggerFactory
import org.springframework.jdbc.BadSqlGrammarException

@ExperimentalContracts
class SqlCache(
  private val name: String,
  private val jooq: DSLContext,
  private val mapper: ObjectMapper,
  private val coroutineContext: CoroutineContext?,
  private val clock: Clock,
  private val sqlRetryProperties: SqlRetryProperties,
  tableNamespace: String?,
  private val cacheMetrics: SqlCacheMetrics,
  private val dynamicConfigService: DynamicConfigService,
  private val sqlConstraints: SqlConstraints,
  private val providerCacheConfiguration: ProviderCacheConfiguration
) : WriteableCache {

  companion object {
    private const val onDemandType = "onDemand"

    private val schemaVersion = SqlSchemaVersion.current()
    private val useRegexp =
      """.*[\?\[].*""".toRegex()
    private val cleanRegexp =
      """\.+\*""".toRegex()

    private val log = LoggerFactory.getLogger(SqlCache::class.java)
  }

  private val sqlNames = SqlNames(tableNamespace, sqlConstraints)

  private var createdTables = ConcurrentSkipListSet<String>()

  private val hexStrings: List<String>

  init {
    log.info("Configured for $name")

    // Pre-generating string representations for all possible byte values
    // to avoid using `String#format` in frequently invoked methods like `getHash`.
    // Invoking `String#format` in these hot methods creates excessive garbage.
    this.hexStrings = arrayListOf()
    for (byte in Byte.MIN_VALUE..Byte.MAX_VALUE) {
      val str = "%02x".format(byte.toByte())
      this.hexStrings.add(str)
    }
  }

  /**
   * Only evicts cache records but not relationship rows
   */
  override fun evictAll(type: String, ids: Collection<String>) {
    if (ids.isEmpty()) {
      return
    }

    log.info("evicting ${ids.size} $type records")

    var deletedCount = 0
    var opCount = 0
    try {
      ids.chunked(dynamicConfigService.getConfig(Int::class.java, "sql.cache.read-batch-size", 500)) { chunk ->
        withRetry(RetryCategory.WRITE) {
          jooq.deleteFrom(table(sqlNames.resourceTableName(type)))
            .where(field("id").`in`(*chunk.toTypedArray()))
            .execute()
        }
        deletedCount += chunk.size
        opCount += 1
      }
    } catch (e: Exception) {
      log.error("error evicting records", e)
    }

    cacheMetrics.evict(
      prefix = name,
      type = type,
      itemCount = ids.size,
      itemsDeleted = deletedCount,
      deleteOperations = opCount
    )
  }

  fun mergeAll(
    type: String,
    agentHint: String?,
    items: MutableCollection<CacheData>?,
    authoritative: Boolean,
    cleanup: Boolean
  ) {
    if (type.isEmpty()) {
      return
    }

    createTables(type)

    if (!providerCacheConfiguration.supportsFullEviction()) {
      if (items.isNullOrEmpty() || items.none { it.id != "_ALL_" }) {
        return
      }
    }

    if (items.isNullOrEmpty()) {
      log.warn("No cacheable items supplied, collection will be cleared (type: {}, agent: {})", type, agentHint)
    }

    var agent: String? = agentHint

    val first: String? = items
      ?.firstOrNull { it.relationships.isNotEmpty() }
      ?.relationships
      ?.keys
      ?.firstOrNull()

    if (first != null && agent == null) {
      agent = first.substringAfter(":", first)
    }

    if (agent == null) {
      log.debug("warning: null agent for type $type")
    }

    val storeResult = if (authoritative) {
      storeAuthoritative(type, agent, items ?: mutableListOf(), cleanup)
    } else {
      storeInformative(type, items ?: mutableListOf(), cleanup)
    }

    cacheMetrics.merge(
      prefix = name,
      type = type,
      itemCount = storeResult.itemCount.get(),
      itemsStored = storeResult.itemsStored.get(),
      relationshipCount = storeResult.relationshipCount.get(),
      relationshipsStored = storeResult.relationshipsStored.get(),
      selectOperations = storeResult.selectQueries.get(),
      writeOperations = storeResult.writeQueries.get(),
      deleteOperations = storeResult.deleteQueries.get(),
      duplicates = storeResult.duplicates.get()
    )
  }

  override fun mergeAll(type: String, items: MutableCollection<CacheData>?) {
    mergeAll(type, null, items, true, true)
  }

  /**
   * Retrieves all the items for the specified type
   *
   * @param type the type for which to retrieve items
   * @return all the items for the type
   */
  override fun getAll(type: String): MutableCollection<CacheData> {
    return getAll(type, null as CacheFilter?)
  }

  override fun getAll(type: String, cacheFilter: CacheFilter?): MutableCollection<CacheData> {
    val relationshipPrefixes = getRelationshipFilterPrefixes(cacheFilter)

    val result = if (relationshipPrefixes.isEmpty()) {
      getDataWithoutRelationships(type)
    } else {
      getDataWithRelationships(type, relationshipPrefixes)
    }

    if (result.selectQueries > -1) {
      cacheMetrics.get(
        prefix = name,
        type = type,
        itemCount = result.data.size,
        requestedSize = result.data.size,
        relationshipsRequested = result.relPointers.size,
        selectOperations = result.selectQueries,
        async = result.withAsync
      )
    }

    return mergeDataAndRelationships(result.data, result.relPointers, relationshipPrefixes)
  }

  /**
   * Retrieves the items for the specified type matching the provided ids
   *
   * @param type the type for which to retrieve items
   * @param ids the ids
   * @return the items matching the type and ids
   */
  override fun getAll(type: String, ids: MutableCollection<String>?): MutableCollection<CacheData> {
    return getAll(type, ids, null as CacheFilter?)
  }

  override fun getAll(
    type: String,
    ids: MutableCollection<String>?,
    cacheFilter: CacheFilter?
  ): MutableCollection<CacheData> {
    if (ids.isNullOrEmpty()) {
      cacheMetrics.get(
        prefix = name,
        type = type,
        itemCount = 0,
        requestedSize = 0,
        relationshipsRequested = 0,
        selectOperations = 0
      )
      return mutableListOf()
    }

    val relationshipPrefixes = getRelationshipFilterPrefixes(cacheFilter)

    val result = if (relationshipPrefixes.isEmpty()) {
      getDataWithoutRelationships(type, ids)
    } else {
      getDataWithRelationships(type, ids, relationshipPrefixes)
    }

    if (result.selectQueries > -1) {
      cacheMetrics.get(
        prefix = name,
        type = type,
        itemCount = result.data.size,
        requestedSize = ids.size,
        relationshipsRequested = result.relPointers.size,
        selectOperations = result.selectQueries,
        async = result.withAsync
      )
    }

    return mergeDataAndRelationships(result.data, result.relPointers, relationshipPrefixes)
  }

  /**
   * Retrieves the items for the specified type matching the provided identifiers
   *
   * @param type the type for which to retrieve items
   * @param identifiers the identifiers
   * @return the items matching the type and identifiers
   */
  override fun getAll(type: String, vararg identifiers: String?): MutableCollection<CacheData> {
    val ids = mutableListOf<String>()
    identifiers.forEach { ids.add(it!!) }
    return getAll(type, ids)
  }

  override fun supportsGetAllByApplication(): Boolean {
    return true
  }

  override fun getAllByApplication(
    type: String,
    application: String,
    cacheFilter: CacheFilter?
  ): Map<String, MutableCollection<CacheData>> {
    val relationshipPrefixes = getRelationshipFilterPrefixes(cacheFilter)

    val result = if (relationshipPrefixes.isEmpty()) {
      getDataWithoutRelationshipsByApp(type, application)
    } else {
      getDataWithRelationshipsByApp(type, application, relationshipPrefixes)
    }

    if (result.selectQueries > -1) {
      cacheMetrics.get(
        prefix = name,
        type = type,
        itemCount = result.data.size,
        requestedSize = result.data.size,
        relationshipsRequested = result.relPointers.size,
        selectOperations = result.selectQueries,
        async = wasAsync()
      )
    }

    return mapOf(type to mergeDataAndRelationships(result.data, result.relPointers, relationshipPrefixes))
  }

  override fun getAllByApplication(
    types: Collection<String>,
    application: String,
    cacheFilters: Map<String, CacheFilter?>
  ): Map<String, MutableCollection<CacheData>> {
    val result = mutableMapOf<String, MutableCollection<CacheData>>()

    if (coroutineContext.useAsync(this::asyncEnabled)) {
      val scope = CatsCoroutineScope(coroutineContext)

      types.chunked(dynamicConfigService.getConfig(Int::class.java, "sql.cache.max-query-concurrency", 4)) { batch ->
        val deferred = batch.map { type ->
          scope.async { getAllByApplication(type, application, cacheFilters[type]) }
        }

        runBlocking {
          deferred.awaitAll().forEach { result.putAll(it) }
        }
      }
    } else {
      types.forEach { type ->
        result.putAll(getAllByApplication(type, application, cacheFilters[type]))
      }
    }

    return result
  }

  override fun merge(type: String, cacheData: CacheData) {
    mergeAll(type, null, mutableListOf(cacheData), true, false)
  }

  /**
   * Retrieves all the identifiers for a type
   *
   * @param type the type for which to retrieve identifiers
   * @return the identifiers for the type
   */
  override fun getIdentifiers(type: String): MutableCollection<String> {
    val ids = try {
      withRetry(RetryCategory.READ) {
        jooq.select(field("id"))
          .from(table(sqlNames.resourceTableName(type)))
          .fetch()
          .intoSet(field("id"), String::class.java)
      }
    } catch (e: BadSqlGrammarException) {
      suppressedLog("Failed getting ids for type $type", e)
      return mutableListOf()
    }

    cacheMetrics.get(
      prefix = name,
      type = type,
      itemCount = ids.size,
      requestedSize = ids.size,
      relationshipsRequested = 0,
      selectOperations = 1
    )

    return ids
  }

  /**
   * Filters the supplied list of identifiers to only those that exist in the cache.
   *
   * @param type the type of the item
   * @param identifiers the identifiers for the items
   * @return the list of identifiers that are present in the cache from the provided identifiers
   */
  override fun existingIdentifiers(type: String, identifiers: MutableCollection<String>): MutableCollection<String> {
    var selects = 0
    var withAsync = false
    val existing = mutableListOf<String>()
    val batchSize = dynamicConfigService.getConfig(Int::class.java, "sql.cache.read-batch-size", 500)

    if (coroutineContext.useAsync(identifiers.size, this::useAsync)) {
      withAsync = true
      val scope = CatsCoroutineScope(coroutineContext)

      identifiers.chunked(batchSize).chunked(
        dynamicConfigService.getConfig(Int::class.java, "sql.cache.max-query-concurrency", 4)
      ) { batch ->
        val deferred = batch.map { ids ->
          scope.async {
            selectIdentifiers(type, ids)
          }
        }
        runBlocking {
          existing.addAll(deferred.awaitAll().flatten())
        }
        selects += deferred.size
      }
    } else {
      identifiers.chunked(batchSize) { chunk ->
        existing.addAll(selectIdentifiers(type, chunk))
        selects += 1
      }
    }

    cacheMetrics.get(
      prefix = name,
      type = type,
      itemCount = 0,
      requestedSize = 0,
      relationshipsRequested = 0,
      selectOperations = selects,
      async = withAsync
    )

    return existing
  }

  /**
   * Returns the identifiers for the specified type that match the provided glob.
   *
   * @param type The type for which to retrieve identifiers
   * @param glob The glob to match against the identifiers
   * @return the identifiers for the type that match the glob
   */
  override fun filterIdentifiers(type: String, glob: String?): MutableCollection<String> {
    if (glob == null) {
      return mutableSetOf()
    }

    val sql = if (glob.matches(useRegexp)) {
      val filter = glob.replace("?", ".", true).replace("*", ".*").replace(cleanRegexp, ".*")
      jooq
        .select(field("id"))
        .from(table(sqlNames.resourceTableName(type)))
        .where(field("id").likeRegex("^$filter$"))
    } else {
      jooq
        .select(field("id"))
        .from(table(sqlNames.resourceTableName(type)))
        // The underscore is treated as a single character wildcard in currently supported sql backends (mysql/psql)
        // leading to inconsistencies in current usages of `filterIdentifiers()`.
        //
        // If single character wildcard is desired, use '?' rather than '_'.
        .where(field("id").like(glob.replace('*', '%').replace("_", """\_""")))
    }

    val ids = try {
      withRetry(RetryCategory.READ) {
        sql
          .fetch(field("id"), String::class.java)
      }
    } catch (e: Exception) {
      suppressedLog("Failed searching for identifiers type: $type glob: $glob reason: ${e.message}", e)
      mutableSetOf<String>()
    }

    cacheMetrics.get(
      prefix = name,
      type = type,
      itemCount = ids.size,
      requestedSize = ids.size,
      relationshipsRequested = 0,
      selectOperations = 1
    )

    return ids
  }

  /**
   * Gets a single item from the cache by type and id
   *
   * @param type the type of the item
   * @param id the id of the item
   * @return the item matching the type and id
   */
  override fun get(type: String, id: String?): CacheData? {
    return get(type, id, null)
  }

  override fun get(type: String, id: String?, cacheFilter: CacheFilter?): CacheData? {
    val result = getAll(type, Arrays.asList<String>(id), cacheFilter)
    return if (result.isEmpty()) {
      null
    } else result.iterator().next()
  }

  override fun evict(type: String, id: String) {
    evictAll(type, listOf(id))
  }

  fun cleanOnDemand(maxAgeMs: Long): Int {
    val toClean = withRetry(RetryCategory.READ) {
      jooq.select(field("id"))
        .from(table(sqlNames.resourceTableName(onDemandType)))
        .where(field("last_updated").lt(clock.millis() - maxAgeMs))
        .fetch()
        .into(String::class.java)
    }

    evictAll(onDemandType, toClean)

    return toClean.size
  }

  private fun storeAuthoritative(
    type: String,
    agentHint: String?,
    items: MutableCollection<CacheData>,
    cleanup: Boolean
  ): StoreResult {
    val result = StoreResult()
    result.itemCount.addAndGet(items.size)

    val agent = if (type == ON_DEMAND.ns) {
      // onDemand keys aren't initially written by the agents that update and expire them. since agent is
      // part of the primary key, we need to ensure a consistent value across initial and subsequent writes
      ON_DEMAND.ns
    } else {
      agentHint ?: "unknown"
    }

    val existingHashIds = getHashIds(type, agent)
    result.selectQueries.incrementAndGet()

    val existingHashes = existingHashIds // ids previously store by the calling caching agent
      .asSequence()
      .map { it.body_hash }
      .toSet()
    val existingIds = existingHashIds
      .asSequence()
      .map { it.id }
      .toSet()
    val currentIds = mutableSetOf<String>() // current ids from the caching agent
    val toStore = mutableListOf<String>() // ids that are new or changed
    val bodies = mutableMapOf<String, String>() // id to body
    val hashes = mutableMapOf<String, String>() // id to sha256(body)
    val apps = mutableMapOf<String, String>()

    items.filter { it.id.length > sqlConstraints.maxIdLength }
      .forEach {
        log.error("Dropping ${it.id} - character length exceeds MAX_ID_LENGTH ($sqlConstraints.maxIdLength)")
      }

    items
      .filter { it.id != "_ALL_" && it.id.length <= sqlConstraints.maxIdLength }
      .forEach {
        if (!currentIds.add(it.id)) {
            log.warn("agent: '${agent}': type: '$type': only one item with id '${it.id}' allowed")
            result.duplicates.incrementAndGet()
            // Skip the rest of this iteration
            return@forEach
        }
        val nullKeys = it.attributes
          .filter { e -> e.value == null }
          .keys
        nullKeys.forEach { na -> it.attributes.remove(na) }

        if (it.attributes.containsKey("application")) {
          apps[it.id] = it.attributes["application"] as String
        }

        val keysToNormalize = it.relationships.keys.filter { k -> k.contains(':') }
        if (keysToNormalize.isNotEmpty()) {
          val normalized = normalizeRelationships(it.relationships, emptyList())
          keysToNormalize.forEach { k -> it.relationships.remove(k) }
          it.relationships.putAll(normalized)
        }

        val body: String? = mapper.writeValueAsString(it)
        val bodyHash = getHash(body)

        if (body != null && bodyHash != null && !existingHashes.contains(bodyHash)) {
          toStore.add(it.id)
          bodies[it.id] = body
          hashes[it.id] = bodyHash
        }
      }

    val now = clock.millis()

    toStore.chunked(dynamicConfigService.getConfig(Int::class.java, "sql.cache.write-batch-size", 100)) { chunk ->
      try {
        val insert = jooq.insertInto(
          table(sqlNames.resourceTableName(type)),
          field("id"),
          field("agent"),
          field("application"),
          field("body_hash"),
          field("body"),
          field("last_updated")
        )

        insert.apply {
          chunk.forEach {
            values(it, sqlNames.checkAgentName(agent), apps[it], hashes[it], bodies[it], now)
            when (jooq.dialect()) {
              SQLDialect.POSTGRES ->
                onConflict(field("id"), field("agent"))
                  .doUpdate()
                  .set(field("application"), SqlUtil.excluded(field("application")) as Any)
                  .set(field("body_hash"), SqlUtil.excluded(field("body_hash")) as Any)
                  .set(field("body"), SqlUtil.excluded(field("body")) as Any)
                  .set(field("last_updated"), SqlUtil.excluded(field("last_updated")) as Any)
              else ->
                onDuplicateKeyUpdate()
                  .set(field("application"), MySQLDSL.values(field("application")) as Any)
                  .set(field("body_hash"), MySQLDSL.values(field("body_hash")) as Any)
                  .set(field("body"), MySQLDSL.values(field("body")) as Any)
                  .set(field("last_updated"), MySQLDSL.values(field("last_updated")) as Any)
            }
          }
        }

        withRetry(RetryCategory.WRITE) {
          insert.execute()
        }
        result.itemsStored.addAndGet(chunk.size)
        result.writeQueries.incrementAndGet()
      } catch (e: DataAccessException) {
        log.error("Error inserting ids: $chunk", e)
      } catch (e: SQLDialectNotSupportedException) {
        chunk.forEach {
          val exists = withRetry(RetryCategory.READ) {
            jooq.fetchExists(
              jooq.select()
                .from(sqlNames.resourceTableName(type))
                .where(field("id").eq(it), field("agent").eq(sqlNames.checkAgentName(agent)))
                .forUpdate()
            )
          }
          result.selectQueries.incrementAndGet()
          if (exists) {
            withRetry(RetryCategory.WRITE) {
              jooq.update(table(sqlNames.resourceTableName(type)))
                .set(field("application"), apps[it])
                .set(field("body_hash"), hashes[it])
                .set(field("body"), bodies[it])
                .set(field("last_updated"), clock.millis())
                .where(field("id").eq(it), field("agent").eq(sqlNames.checkAgentName(agent)))
                .execute()
            }
            result.writeQueries.incrementAndGet()
            result.itemsStored.incrementAndGet()
          } else {
            withRetry(RetryCategory.WRITE) {
              jooq.insertInto(
                table(sqlNames.resourceTableName(type)),
                field("id"),
                field("agent"),
                field("application"),
                field("body_hash"),
                field("body"),
                field("last_updated")
              ).values(
                it,
                sqlNames.checkAgentName(agent),
                apps[it],
                hashes[it],
                bodies[it],
                clock.millis()
              ).execute()
            }
            result.writeQueries.incrementAndGet()
            result.itemsStored.incrementAndGet()
          }
        }
      }
    }

    if (!cleanup) {
      return result
    }

    val toDelete = existingIds
      .asSequence()
      .filter { !currentIds.contains(it) }
      .toSet()

    evictAll(type, toDelete)

    return result
  }

  private fun storeInformative(type: String, items: MutableCollection<CacheData>, cleanup: Boolean): StoreResult {
    val result = StoreResult()

    val sourceAgents = items.filter { it.relationships.isNotEmpty() }
      .map { it.relationships.keys }
      .flatten()
      .toSet()

    if (sourceAgents.isEmpty()) {
      log.info("no relationships found for type $type")
      return result
    }

    val existingFwdRelIds = sourceAgents
      .map {
        result.selectQueries.incrementAndGet()
        getRelationshipKeys(type, it)
      }
      .flatten()

    val existingRevRelTypes = mutableSetOf<String>()
    items
      .filter { it.id != "_ALL_" }
      .forEach { cacheData ->
        cacheData.relationships.entries.forEach { rels ->
          val relType = rels.key.substringBefore(delimiter = ":", missingDelimiterValue = "")
          existingRevRelTypes.add(relType)
        }
      }

    existingRevRelTypes.filter { !createdTables.contains(it) }
      .forEach { createTables(it) }

    val existingRevRelIds = existingRevRelTypes
      .map { relType ->
        sourceAgents
          .map { agent ->
            result.selectQueries.incrementAndGet()
            getRelationshipKeys(relType, type, agent)
          }
          .flatten()
      }
      .flatten()

    val oldFwdIds: Map<String, String> = existingFwdRelIds
      .asSequence()
      .map { it.key() to it.uuid }
      .toMap()

    val oldRevIds = mutableMapOf<String, String>()
    val oldRevIdsToType = mutableMapOf<String, String>()

    existingRevRelIds
      .forEach {
        oldRevIds[it.key()] = it.uuid
        oldRevIdsToType[it.key()] = it.rel_agent.substringBefore(delimiter = ":", missingDelimiterValue = "")
      }

    val currentIds = mutableSetOf<String>()
    val newFwdRelPointers = mutableMapOf<String, MutableList<RelPointer>>()
    val newRevRelIds = mutableSetOf<String>()

    items
      .filter { it.id != "_ALL_" && it.id.length <= sqlConstraints.maxIdLength }
      .forEach { cacheData ->
        cacheData.relationships.entries.forEach { rels ->
          val relType = rels.key.substringBefore(delimiter = ":", missingDelimiterValue = "")
          rels.value.filter { it.length <= sqlConstraints.maxIdLength }
            .forEach { r ->
              val fwdKey = "${cacheData.id}|$r"
              val revKey = "$r|${cacheData.id}"
              currentIds.add(fwdKey)
              currentIds.add(revKey)

              result.relationshipCount.incrementAndGet()

              if (!oldFwdIds.contains(fwdKey)) {
                newFwdRelPointers.getOrPut(relType) { mutableListOf() }
                  .add(RelPointer(cacheData.id, r, rels.key))
              }

              if (!oldRevIds.containsKey(revKey)) {
                newRevRelIds.add(revKey)
              }
            }
        }
      }

    newFwdRelPointers.forEach { (relType, pointers) ->
      val now = clock.millis()
      var ulid = ULID().nextValue()

      pointers.chunked(dynamicConfigService.getConfig(Int::class.java, "sql.cache.write-batch-size", 100)) { chunk ->
        try {
          val insert = jooq.insertInto(
            table(sqlNames.relTableName(type)),
            field("uuid"),
            field("id"),
            field("rel_id"),
            field("rel_agent"),
            field("rel_type"),
            field("last_updated")
          )

          insert.apply {
            chunk.forEach {
              values(ulid.toString(), it.id, it.rel_id, sqlNames.checkAgentName(it.rel_type), relType, now)
              ulid = ULID().nextMonotonicValue(ulid)
            }
          }

          withRetry(RetryCategory.WRITE) {
            insert.execute()
          }
          result.writeQueries.incrementAndGet()
          result.relationshipsStored.addAndGet(chunk.size)
        } catch (e: Exception) {
          log.error("Error inserting forward relationships for $type -> $relType", e)
        }
      }

      pointers.asSequence().filter { newRevRelIds.contains("${it.rel_id}|${it.id}") }
        .chunked(dynamicConfigService.getConfig(Int::class.java, "sql.cache.write-batch-size", 100)) { chunk ->
          try {
            val insert = jooq.insertInto(
              table(sqlNames.relTableName(relType)),
              field("uuid"),
              field("id"),
              field("rel_id"),
              field("rel_agent"),
              field("rel_type"),
              field("last_updated")
            )

            insert.apply {
              chunk.forEach {
                values(ulid.toString(), it.rel_id, it.id, sqlNames.checkAgentName(it.rel_type), type, now)
                ulid = ULID().nextMonotonicValue(ulid)
              }
            }

            withRetry(RetryCategory.WRITE) {
              insert.execute()
            }
            result.writeQueries.incrementAndGet()
            result.relationshipsStored.addAndGet(chunk.size)
          } catch (e: Exception) {
            log.error("Error inserting reverse relationships for $relType -> $type", e)
          }
        }.toList()
    }

    if (!cleanup) {
      return result
    }

    val fwdToDelete = oldFwdIds.filter { !currentIds.contains(it.key) }
    val revToDelete = oldRevIds.filter { !currentIds.contains(it.key) }

    if (fwdToDelete.isNotEmpty() || revToDelete.isNotEmpty()) {
      try {
        fwdToDelete.forEach {
          withRetry(RetryCategory.WRITE) {
            jooq.deleteFrom(table(sqlNames.relTableName(type)))
              .where(field("uuid").eq(it.value))
              .execute()
          }
          result.deleteQueries.incrementAndGet()
        }
        revToDelete.forEach {
          if (oldRevIdsToType.getOrDefault(it.key, "").isNotBlank()) {
            withRetry(RetryCategory.WRITE) {
              jooq.deleteFrom(table(sqlNames.relTableName(oldRevIdsToType[it.key]!!)))
                .where(field("uuid").eq(it.value))
                .execute()
            }
            result.deleteQueries.incrementAndGet()
          } else {
            log.warn("Couldn't delete ${it.key}, no mapping to type")
          }
        }
      } catch (e: Exception) {
        log.error("Error deleting stale relationships", e)
      }
    }

    return result
  }

  private fun createTables(type: String) {
    if (!createdTables.contains(type)) {
      try {
        withRetry(RetryCategory.WRITE) {
          SqlUtil.createTableLike(jooq, sqlNames.resourceTableName(type), "cats_v${schemaVersion}_resource_template")
          SqlUtil.createTableLike(jooq, sqlNames.relTableName(type), "cats_v${schemaVersion}_rel_template")
        }

        createdTables.add(type)
      } catch (e: Exception) {
        log.error("Error creating tables for type $type", e)
      }
    }
    if (!createdTables.contains(onDemandType)) {
      // TODO not sure if best schema for onDemand
      try {
        withRetry(RetryCategory.WRITE) {
          SqlUtil.createTableLike(jooq, sqlNames.resourceTableName(onDemandType), "cats_v${schemaVersion}_resource_template")
          SqlUtil.createTableLike(jooq, sqlNames.relTableName(onDemandType), "cats_v${schemaVersion}_rel_template")
        }

        createdTables.add(onDemandType)
      } catch (e: Exception) {
        log.error("Error creating $onDemandType table", e)
      }
    }
  }

  private fun getRelationshipFilterPrefixes(cacheFilter: CacheFilter?): List<String> {
    return if (cacheFilter == null) {
      listOf("ALL")
    } else {
      try {
        (cacheFilter as RelationshipCacheFilter).allowableRelationshipPrefixes
      } catch (e: Exception) {
        log.warn("Failed reading cacheFilter allowableRelationshipPrefixes", e)
        emptyList<String>()
      }
    }
  }

  private fun getHash(body: String?): String? {
    if (body.isNullOrBlank()) {
      return null
    }
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
        .digest(body.toByteArray())
      // The hash length is known, so a `StringBuilder` with a predefined capacity is used
      // to prevent unnecessary array allocations inside the StringBuilder.
      val builder = StringBuilder(64)
      for (byte in digest) {
        // Uses pre-generated string representations for each byte to optimize performance.
        builder.append(this.hexStrings[byte - Byte.MIN_VALUE])
      }
      builder.toString()
    } catch (e: Exception) {
      log.error("error calculating hash for body: $body", e)
      null
    }
  }

  private fun getHashIds(type: String, agent: String?): List<HashId> {
    return withRetry(RetryCategory.READ) {
      jooq
        .select(field("body_hash"), field("id"))
        .from(table(sqlNames.resourceTableName(type)))
        .where(
          field("agent").eq(sqlNames.checkAgentName(agent))
        )
        .fetch()
        .into(HashId::class.java)
    }
  }

  private fun getRelationshipKeys(type: String, sourceAgent: String): MutableList<RelId> {
    return withRetry(RetryCategory.READ) {
      jooq
        .select(field("uuid"), field("id"), field("rel_id"), field("rel_agent"))
        .from(table(sqlNames.relTableName(type)))
        .where(field("rel_agent").eq(sqlNames.checkAgentName(sourceAgent)))
        .fetch()
        .into(RelId::class.java)
    }
  }

  private fun getRelationshipKeys(type: String, origType: String, sourceAgent: String): MutableList<RelId> {
    return withRetry(RetryCategory.READ) {
      jooq
        .select(field("uuid"), field("id"), field("rel_id"), field("rel_agent"))
        .from(table(sqlNames.relTableName(type)))
        .where(
          field("rel_agent").eq(sqlNames.checkAgentName(sourceAgent)),
          field("rel_type").eq(origType)
        )
        .fetch()
        .into(RelId::class.java)
    }
  }

  private fun getDataWithoutRelationships(type: String): DataWithRelationshipPointersResult {
    return getDataWithoutRelationships(type, emptyList())
  }

  private fun getDataWithoutRelationships(
    type: String,
    ids: Collection<String>
  ): DataWithRelationshipPointersResult {
    val cacheData = mutableListOf<CacheData>()
    val relPointers = mutableSetOf<RelPointer>()
    val batchSize = dynamicConfigService.getConfig(Int::class.java, "sql.cache.read-batch-size", 500)
    var selectQueries = 0
    var withAsync = false

    try {
      if (ids.isEmpty()) {
        withRetry(RetryCategory.READ) {
          cacheData.addAll(
            jooq.select(field("body"))
              .from(table(sqlNames.resourceTableName(type)))
              .fetch()
              .getValues(0)
              .asSequence()
              .map { mapper.readValue(it as String, DefaultJsonCacheData::class.java) }
              .toList()
          )
        }
        selectQueries += 1
      } else {
        if (coroutineContext.useAsync(ids.size, this::useAsync)) {
          withAsync = true
          val scope = CatsCoroutineScope(coroutineContext)

          ids.chunked(batchSize).chunked(
            dynamicConfigService.getConfig(Int::class.java, "sql.cache.max-query-concurrency", 4)
          ) { batch ->
            val deferred = batch.map { ids ->
              scope.async { selectBodies(type, ids) }
            }
            runBlocking {
              cacheData.addAll(deferred.awaitAll().flatten())
            }
            selectQueries += deferred.size
          }
        } else {
          ids.chunked(batchSize) { chunk ->
            cacheData.addAll(selectBodies(type, chunk))
            selectQueries += 1
          }
        }
      }

      return DataWithRelationshipPointersResult(cacheData, relPointers, selectQueries, withAsync)
    } catch (e: Exception) {
      suppressedLog("Failed selecting ids for type $type", e)

      cacheMetrics.get(
        prefix = name,
        type = type,
        itemCount = 0,
        requestedSize = -1,
        relationshipsRequested = -1,
        selectOperations = selectQueries,
        async = withAsync
      )

      selectQueries = -1

      return DataWithRelationshipPointersResult(mutableListOf(), mutableSetOf(), selectQueries, withAsync)
    }
  }

  private fun getDataWithoutRelationshipsByApp(type: String, application: String): DataWithRelationshipPointersResult {
    val cacheData = mutableListOf<CacheData>()
    val relPointers = mutableSetOf<RelPointer>()
    var selectQueries = 0

    try {
      withRetry(RetryCategory.READ) {
        cacheData.addAll(
          jooq.select(field("body"))
            .from(table(sqlNames.resourceTableName(type)))
            .where(field("application").eq(application))
            .fetch()
            .getValues(0)
            .asSequence()
            .map { mapper.readValue(it as String, DefaultJsonCacheData::class.java) }
            .toList()
        )
      }
      selectQueries += 1
      return DataWithRelationshipPointersResult(cacheData, relPointers, selectQueries, false)
    } catch (e: Exception) {
      suppressedLog("Failed selecting resources of type $type for application $application", e)

      cacheMetrics.get(
        prefix = name,
        type = type,
        itemCount = 0,
        requestedSize = -1,
        relationshipsRequested = -1,
        selectOperations = selectQueries,
        async = wasAsync()
      )

      selectQueries = -1

      return DataWithRelationshipPointersResult(mutableListOf(), mutableSetOf(), selectQueries, false)
    }
  }

  private fun getDataWithRelationshipsByApp(
    type: String,
    application: String,
    relationshipPrefixes: List<String>
  ): DataWithRelationshipPointersResult {

    /*
      select body, null as id, null as rel_id, null as rel_type from cats_v1_b_instances
        where application = 'titusagent'
          UNION ALL
      select null as body, rel.id, rel.rel_id, rel.rel_type from cats_v1_b_instances as r
        left join cats_v1_b_instances_rel as rel on rel.id=r.id where r.application = "titusagent"
        group by rel.rel_id, rel.id, rel.rel_type;
     */
    val cacheData = mutableListOf<CacheData>()
    val relPointers = mutableSetOf<RelPointer>()
    var selectQueries = 0

    val relWhere = getRelWhere(relationshipPrefixes, field("r.application").eq(application))

    try {
      val resultSet = withRetry(RetryCategory.READ) {
        jooq
          .select(
            field("body").`as`("body"),
            field(sql("null")).`as`("id"),
            field(sql("null")).`as`("rel_id"),
            field(sql("null")).`as`("rel_type")
          )
          .from(table(sqlNames.resourceTableName(type)))
          .where(field("application").eq(application))
          .unionAll(
            jooq.select(
              field(sql("null")).`as`("body"),
              field("rel.id").`as`("id"),
              field("rel.rel_id").`as`("rel_id"),
              field("rel.rel_type").`as`("rel_type")
            )
              .from(table(sqlNames.resourceTableName(type)).`as`("r"))
              .innerJoin(table(sqlNames.relTableName(type)).`as`("rel"))
              .on(sql("rel.id=r.id"))
              .where(relWhere)
              .groupBy(
                field("rel_id"),
                field("id"),
                field("rel_type")
              )
          )
          .fetch()
          .intoResultSet()
      }
      parseCacheRelResultSet(type, resultSet, cacheData, relPointers)
      selectQueries += 1
      return DataWithRelationshipPointersResult(cacheData, relPointers, selectQueries, false)
    } catch (e: Exception) {
      suppressedLog("Failed selecting resources of type $type for application $application", e)

      cacheMetrics.get(
        prefix = name,
        type = type,
        itemCount = 0,
        requestedSize = -1,
        relationshipsRequested = -1,
        selectOperations = selectQueries,
        async = wasAsync()
      )

      selectQueries = -1

      return DataWithRelationshipPointersResult(mutableListOf(), mutableSetOf(), selectQueries, false)
    }
  }

  private fun getDataWithRelationships(
    type: String,
    relationshipPrefixes: List<String>
  ):
    DataWithRelationshipPointersResult {
      return getDataWithRelationships(type, emptyList(), relationshipPrefixes)
    }

  private fun getDataWithRelationships(
    type: String,
    ids: Collection<String>,
    relationshipPrefixes: List<String>
  ): DataWithRelationshipPointersResult {
    val cacheData = mutableListOf<CacheData>()
    val relPointers = mutableSetOf<RelPointer>()
    var selectQueries = 0
    var withAsync = false
    val batchSize = dynamicConfigService.getConfig(Int::class.java, "sql.cache.read-batch-size", 500)

    /*
        Approximating the following query pattern in jooq:

        (select body, null as r_id, null as rel, null as rel_type from `cats_v1_a_applications` where id IN
        ('aws:applications:spintest', 'aws:applications:spindemo')) UNION
        (select null as body, id as r_id, rel_id as rel, rel_type from `cats_v1_a_applications_rel` where id IN
        ('aws:applications:spintest', 'aws:applications:spindemo') and rel_type like 'load%');
    */

    try {
      if (ids.isEmpty()) {

        val relWhere = getRelWhere(relationshipPrefixes)

        val resultSet = withRetry(RetryCategory.READ) {
          jooq
            .select(
              field("body").`as`("body"),
              field(sql("null")).`as`("id"),
              field(sql("null")).`as`("rel_id"),
              field(sql("null")).`as`("rel_type")
            )
            .from(table(sqlNames.resourceTableName(type)))
            .unionAll(
              jooq.select(
                field(sql("null")).`as`("body"),
                field("id").`as`("id"),
                field("rel_id").`as`("rel_id"),
                field("rel_type").`as`("rel_type")
              )
                .from(table(sqlNames.relTableName(type)))
                .where(relWhere)
            )
            .fetch()
            .intoResultSet()
        }

        parseCacheRelResultSet(type, resultSet, cacheData, relPointers)
        selectQueries += 1
      } else {
        if (coroutineContext.useAsync(ids.size, this::useAsync)) {
          withAsync = true

          ids.chunked(batchSize).chunked(
            dynamicConfigService.getConfig(Int::class.java, "sql.cache.max-query-concurrency", 4)
          ) { batch ->
            val scope = CatsCoroutineScope(coroutineContext)

            val deferred = batch.map { chunk ->
              scope.async {
                selectBodiesWithRelationships(type, relationshipPrefixes, chunk)
              }
            }

            runBlocking {
              deferred.awaitAll()
            }.forEach { resultSet ->
              parseCacheRelResultSet(type, resultSet, cacheData, relPointers)
              selectQueries += 1
            }
          }
        } else {
          ids.chunked(batchSize) { chunk ->
            val resultSet = selectBodiesWithRelationships(type, relationshipPrefixes, chunk)

            parseCacheRelResultSet(type, resultSet, cacheData, relPointers)
            selectQueries += 1
          }
        }
      }
      return DataWithRelationshipPointersResult(cacheData, relPointers, selectQueries, withAsync)
    } catch (e: Exception) {
      suppressedLog("Failed selecting ids for type $type", e)

      cacheMetrics.get(
        prefix = name,
        type = type,
        itemCount = 0,
        requestedSize = -1,
        relationshipsRequested = -1,
        selectOperations = selectQueries,
        async = withAsync
      )

      selectQueries = -1

      return DataWithRelationshipPointersResult(mutableListOf(), mutableSetOf(), selectQueries, withAsync)
    }
  }

  private fun selectBodies(type: String, ids: List<String>): Collection<CacheData> {
    return withRetry(RetryCategory.READ) {
      jooq.select(field("body"))
        .from(table(sqlNames.resourceTableName(type)))
        .where(field("ID").`in`(*ids.toTypedArray()))
        .fetch()
        .getValues(0)
        .map { mapper.readValue(it as String, DefaultJsonCacheData::class.java) }
        .toList()
    }
  }

  private fun selectBodiesWithRelationships(
    type: String,
    relationshipPrefixes: List<String>,
    ids: List<String>
  ): ResultSet {
    val where = field("ID").`in`(*ids.toTypedArray())

    val relWhere = getRelWhere(relationshipPrefixes, where)

    return withRetry(RetryCategory.READ) {
      jooq
        .select(
          field("body").`as`("body"),
          field(sql("null")).`as`("id"),
          field(sql("null")).`as`("rel_id"),
          field(sql("null")).`as`("rel_type")
        )
        .from(table(sqlNames.resourceTableName(type)))
        .where(where)
        .unionAll(
          jooq.select(
            field(sql("null")).`as`("body"),
            field("id").`as`("id"),
            field("rel_id").`as`("rel_id"),
            field("rel_type").`as`("rel_type")
          )
            .from(table(sqlNames.relTableName(type)))
            .where(relWhere)
        )
        .fetch()
        .intoResultSet()
    }
  }

  private fun selectIdentifiers(type: String, ids: List<String>): MutableCollection<String> {
    return withRetry(RetryCategory.READ) {
      jooq.select(field("id"))
        .from(table(sqlNames.resourceTableName(type)))
        .where(field("id").`in`(*ids.toTypedArray()))
        .fetch()
        .intoSet(field("id"), String::class.java)
    }
  }

  private fun parseCacheRelResultSet(
    type: String,
    resultSet: ResultSet,
    cacheData: MutableList<CacheData>,
    relPointers: MutableSet<RelPointer>
  ) {
    while (resultSet.next()) {
      if (!resultSet.getString(1).isNullOrBlank()) {
        try {
          cacheData.add(mapper.readValue(resultSet.getString(1), DefaultJsonCacheData::class.java))
        } catch (e: Exception) {
          log.error("Failed to deserialize cached value: type $type, body ${resultSet.getString(1)}", e)
        }
      } else {
        try {
          relPointers.add(RelPointer(resultSet.getString(2), resultSet.getString(3), resultSet.getString(4)))
        } catch (e: SQLException) {
          log.error("Error reading relationship of type $type", e)
        }
      }
    }
  }

  private fun mergeDataAndRelationships(
    cacheData: Collection<CacheData>,
    relationshipPointers: Collection<RelPointer>,
    relationshipPrefixes: List<String>
  ): MutableCollection<CacheData> {
    val data = mutableMapOf<String, CacheData>()
    val relKeysToRemove = mutableMapOf<String, MutableSet<String>>()
    val filter = relationshipPrefixes.any { it != "ALL" } || relationshipPrefixes.isEmpty()

    // First merge any duplicate ids in cacheData
    cacheData.forEach { item ->
      if (!data.containsKey(item.id)) {
        data[item.id] = item
        // TODO a CacheSpec unit test verifies that an empty cache filter returns no relationshps,
        // however I think we should leave relationships stored in a key's body and only use filter
        // to prevent fetching more. TODO: update the test?
        if (relationshipPrefixes.isNotEmpty()) {
          if (item.relationships.any { it.key.contains(':') }) {
            data[item.id]!!.relationships.putAll(normalizeRelationships(item.relationships, relationshipPrefixes))
          }
        } else {
          relKeysToRemove.getOrPut(item.id) { mutableSetOf() }
            .addAll(data[item.id]!!.relationships.keys)
        }
      } else {
        // TODO get rid of need for !!s
        val rel = data[item.id]!!.relationships
        val alt = data[item.id]!!.attributes

        if (relationshipPrefixes.isNotEmpty()) {
          normalizeRelationships(item.relationships, relationshipPrefixes).forEach {
            if (rel.contains(it.key)) {
              it.value.forEach { rv ->
                if (!rel[it.key]!!.contains(rv)) {
                  rel[it.key]!!.add(rv)
                }
              }
            } else {
              rel[it.key] = it.value
            }
          }
        } else {
          relKeysToRemove.getOrPut(item.id) { mutableSetOf() }
            .addAll(rel.keys)
        }

        item.attributes.forEach {
          if (!alt.contains(it.key)) {
            alt[it.key] = it.value
          }
        }
      }
    }

    // Then merge in additional relationships
    if (relationshipPrefixes.isNotEmpty()) {
      relationshipPointers
        .filter { data.containsKey(it.id) }
        .forEach { r ->
          val existingRels = data[r.id]!!.relationships
            .getOrPut(r.rel_type) { mutableListOf() }

          if (!existingRels.contains(r.rel_id)) {
            existingRels.add(r.rel_id)
          }
        }
    }

    // TODO this would be unnecessary if we only apply cacheFilters when fetching
    // additional relationships
    if (relKeysToRemove.isEmpty() && filter) {
      data.values.forEach { cd ->
        cd.relationships.keys.forEach { r ->
          if (relationshipPrefixes.none { r.startsWith(it) }) {
            relKeysToRemove.getOrPut(cd.id) { mutableSetOf() }.add(r)
          }
        }
      }
    }

    // TODO same as above
    if (relKeysToRemove.isNotEmpty()) {
      relKeysToRemove.forEach { k, v ->
        v.forEach {
          data[k]?.relationships?.remove(it)
        }
      }
    }

    return data.values
  }

  private fun normalizeRelationships(
    rels: Map<String, Collection<String>>,
    filterPrefixes: List<String>
  ): Map<String, Collection<String>> {
    val filter = filterPrefixes.any { it != "ALL" }
    val relationships = mutableMapOf<String, MutableCollection<String>>()
    rels.entries.forEach {
      val type = it.key.substringBefore(":", missingDelimiterValue = it.key)
      if (!filter || filterPrefixes.any { type.startsWith(it) }) {
        relationships.getOrPut(type) { mutableListOf() }.addAll(it.value)
      }
    }

    return relationships
  }

  private fun getRelWhere(relationshipPrefixes: List<String>, prefix: Condition? = null): Condition {
    var relWhere: Condition = noCondition()

    if (relationshipPrefixes.isNotEmpty() && !relationshipPrefixes.contains("ALL")) {
      relWhere = field("rel_type").like("${relationshipPrefixes[0]}%")

      for (i in 1 until relationshipPrefixes.size) {
        relWhere = relWhere.or(field("rel_type").like("${relationshipPrefixes[i]}%"))
      }
    }

    if (prefix != null) {
      return prefix.and(relWhere)
    }

    return relWhere
  }

  private enum class RetryCategory {
    WRITE, READ
  }

  private fun <T> withRetry(category: RetryCategory, action: () -> T): T {
    return if (category == RetryCategory.WRITE) {
      val retry = Retry.of(
        "sqlWrite",
        RetryConfig.custom<T>()
          .maxAttempts(sqlRetryProperties.transactions.maxRetries)
          .waitDuration(Duration.ofMillis(sqlRetryProperties.transactions.backoffMs))
          .ignoreExceptions(SQLDialectNotSupportedException::class.java)
          .build()
      )

      Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
    } else {
      val retry = Retry.of(
        "sqlRead",
        RetryConfig.custom<T>()
          .maxAttempts(sqlRetryProperties.reads.maxRetries)
          .waitDuration(Duration.ofMillis(sqlRetryProperties.reads.backoffMs))
          .ignoreExceptions(SQLDialectNotSupportedException::class.java)
          .build()
      )

      Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
    }
  }

  @ExperimentalContracts
  private fun useAsync(items: Int): Boolean {
    return dynamicConfigService.getConfig(Int::class.java, "sql.cache.max-query-concurrency", 4) > 1 &&
      items > dynamicConfigService.getConfig(Int::class.java, "sql.cache.read-batch-size", 500) * 2
  }

  @ExperimentalContracts
  private fun asyncEnabled(): Boolean {
    return dynamicConfigService.getConfig(Int::class.java, "sql.cache.max-query-concurrency", 4) > 1
  }

  /**
   * Provides best-effort suppression of "table doesn't exist" exceptions which come up in large volume during initial
   * setup of a new SQL database. This isn't really an error we care to report, as the tables are created on-demand by
   * the cache writer, but may be read against by readers before the caching agents have been run.
   */
  private fun suppressTableNotExistsException(e: Exception): Exception? {
    if (e is BadSqlGrammarException && e.sqlException is SQLSyntaxErrorException) {
      // Best effort suppression of "table doesn't exist" exceptions.
      return if (
        e.sqlException.sqlState.toLowerCase() == "42s02" ||
        e.sqlException.message?.matches(Regex("Table.*doesn't exist")) == true
      ) null else e
    }
    return e
  }

  private fun suppressedLog(message: String, e: Exception) {
    if (suppressTableNotExistsException(e) != null) {
      log.error(message, e)
    }
  }

  private fun wasAsync(): Boolean {
    return Thread.currentThread().name.startsWith(coroutineThreadPrefix)
  }

  // Assists with unit testing
  fun clearCreatedTables() {
    val tables = createdTables.toList()
    createdTables.removeAll(tables)
  }

  data class HashId(
    val body_hash: String,
    val id: String
  )

  data class RelId(
    val uuid: String,
    val id: String,
    val rel_id: String,
    val rel_agent: String
  ) {
    fun key(): String {
      return "$id|$rel_id"
    }
  }

  data class RelPointer(
    val id: String,
    val rel_id: String,
    val rel_type: String
  )

  private data class DataWithRelationshipPointersResult(
    val data: MutableList<CacheData>,
    val relPointers: MutableSet<RelPointer>,
    val selectQueries: Int,
    val withAsync: Boolean = false
  )

  private inner class StoreResult {
    val itemCount = AtomicInteger(0)
    val itemsStored = AtomicInteger(0)
    val relationshipCount = AtomicInteger(0)
    val relationshipsStored = AtomicInteger(0)
    val selectQueries = AtomicInteger(0)
    val writeQueries = AtomicInteger(0)
    val deleteQueries = AtomicInteger(0)
    val duplicates = AtomicInteger(0)
  }
}

@ExperimentalContracts
fun CoroutineContext?.useAsync(size: Int, useAsync: (size: Int) -> Boolean): Boolean {
  contract {
    returns(true) implies (this@useAsync is CoroutineContext)
  }

  return this != null && useAsync.invoke(size)
}

@ExperimentalContracts
fun CoroutineContext?.useAsync(useAsync: () -> Boolean): Boolean {
  contract {
    returns(true) implies (this@useAsync is CoroutineContext)
  }

  return this != null && useAsync.invoke()
}

class CatsCoroutineScope(context: CoroutineContext) : CoroutineScope {
  override val coroutineContext = context
  private val jobs = Job()

  @PreDestroy
  fun killChildJobs() = jobs.cancel()
}
