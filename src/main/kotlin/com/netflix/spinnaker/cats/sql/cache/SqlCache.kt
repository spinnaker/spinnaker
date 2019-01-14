package com.netflix.spinnaker.cats.sql.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CERTIFICATES
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.RESERVATION_REPORTS
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.RESERVED_INSTANCES
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.jooq.util.mysql.MySQLDSL
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Clock
import java.util.Arrays
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger

class SqlCache(
  private val name: String,
  private val jooq: DSLContext,
  private val mapper: ObjectMapper,
  private val clock: Clock,
  private val sqlRetryProperties: SqlRetryProperties,
  private val tablePrefix: String?,
  private val cacheMetrics: SqlCacheMetrics,
  writeChunkSize: Int,
  readChunkSize: Int
) : WriteableCache {

  private val schemaVersion = 1 // TODO: move somewhere sane
  private val relationshipKeyRegex = """^(?:\w+:)?(?:[\w+\-]+)\/([\w+\-]+)\/.*$""".toRegex()
  private val onDemandType = "onDemand"

  // TODO: this may be incorrect for !aws and definitely shouldn't be a hand maintained list here
  private val typesWithoutFwdRelationships = setOf<String>(
    CLUSTERS.ns, CERTIFICATES.ns, NAMED_IMAGES.ns, RESERVATION_REPORTS.ns, RESERVED_INSTANCES.ns,
    "elasticIps", "instanceTypes", "keyPairs", "securityGroups", "subnets", "taggedImage"
  )

  private val writeBatchSize = writeChunkSize
  private val readBatchSize = readChunkSize

  private val log = LoggerFactory.getLogger(javaClass)

  private var createdTables = ConcurrentSkipListSet<String>()

  private val retrySupport = RetrySupport()

  init {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun evictAll(type: String, ids: Collection<String>) {
    // TODO: this only supports deleting resource records, not relationships. Both are primarily deleted
    // TODO: by caching agents themselves. In both cases, this behavior may be incompatible with the
    // TODO: titus streaming agent. Need to verify and potentially customize for this cache implementation.
    var deletedCount = 0
    var opCount = 0
    try {
      ids.chunked(readBatchSize) { chunk ->
        withRetry(RetryCategory.WRITE) {
          jooq.deleteFrom(table(resourceTableName(type)))
            .where("id in (${chunk.joinToString(",") { "'$it'" }})")
            .execute()
        }
      }
      deletedCount += readBatchSize
      opCount += 1
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

  fun mergeAll(type: String,
               agentHint: String?,
               items: MutableCollection<CacheData>?,
               authoritative: Boolean,
               cleanup: Boolean) {
    if (
      type.isEmpty() ||
      items.isNullOrEmpty() ||
      items.none { it.id != "_ALL_" }
    ) {
      return
    }

    createTables(type)

    var location: String? = null
    var agent: String? = agentHint

    val first: String? = items
      .firstOrNull { it.relationships.isNotEmpty() }
      ?.relationships
      ?.keys
      ?.firstOrNull()

    if (first != null) {
      val match = relationshipKeyRegex.find(first)
      if (match?.groupValues?.size == 3) {
        location = match.destructured.component1()
      }
      if (agent == null) {
        agent = first.substringAfter(":", first)
      }
    }
    if (agent == null) {
      log.debug("warning: null agent for type $type")
    }

    val storeResult = if (authoritative) {
      storeAuthoritative(type, agent, items, location, cleanup)
    } else {
      storeInformative(type, items, cleanup)
    }

    cacheMetrics.merge(
      prefix = name,
      type = type,
      itemCount = storeResult.itemCount.get(),
      relationshipCount = 0,
      selectOperations = storeResult.selectQueries.get(),
      insertOperations = storeResult.insertQueries.get(),
      updateOperations = storeResult.updateQueries.get(),
      deleteOperations = storeResult.deleteQueries.get()
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
    var selectQueries = 0

    val cacheData = mutableListOf<CacheData>()
    try {
      withRetry(RetryCategory.READ) {
        cacheData.addAll(
          jooq.select(field("body"))
            .from(table(resourceTableName(type)))
            .fetch()
            .getValues(0)
            .asSequence()
            .map { mapper.readValue(it as String, DefaultCacheData::class.java) }
            .toList()
        )
      }
      selectQueries += 1
    } catch (e: Exception) {
      log.error("Failed selecting ids for type $type", e)

      cacheMetrics.get(
        prefix = name,
        type = type,
        itemCount = 0,
        requestedSize = -1,
        relationshipsRequested = -1,
        selectOperations = selectQueries
      )

      return mutableListOf()
    }

    if (typesWithoutFwdRelationships.contains(type)) {
      cacheMetrics.get(
        prefix = name,
        type = type,
        itemCount = cacheData.size,
        requestedSize = cacheData.size,
        relationshipsRequested = 0,
        selectOperations = selectQueries
      )
      return cacheData
    }

    val relationshipPrefixes = getRelationshipFilterPrefixes(cacheFilter)
    val relationshipPointers = getRelationshipPointers(
      type,
      cacheData.asSequence().map { it.id }.toMutableList(),
      relationshipPrefixes
    )

    val result = mergeDataAndRelationships(cacheData, relationshipPointers.data, relationshipPrefixes)

    cacheMetrics.get(
      prefix = name,
      type = type,
      itemCount = result.size,
      requestedSize = result.size,
      relationshipsRequested = 0,
      selectOperations = selectQueries + relationshipPointers.selectQueries
    )

    return result
  }

  /**
   * Retrieves the items for the specified type matching the provided ids
   *
   * @param type        the type for which to retrieve items
   * @param ids the ids
   * @return the items matching the type and ids
   */
  override fun getAll(type: String, ids: MutableCollection<String>?): MutableCollection<CacheData> {
    return getAll(type, ids, null as CacheFilter?)
  }

  override fun getAll(type: String, ids: MutableCollection<String>?, cacheFilter: CacheFilter?): MutableCollection<CacheData> {
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

    val cacheData = mutableListOf<CacheData>()
    val relationshipPointers = mutableListOf<RelPointer>()
    val relationshipPrefixes = getRelationshipFilterPrefixes(cacheFilter)

    var selectQueries = 0
    try {
      //TODO make configurable
      ids.chunked(readBatchSize) { chunk ->
        val results = withRetry(RetryCategory.READ) {
          jooq.select(field("body"))
            .from(table(resourceTableName(type)))
            .where("ID in (${chunk.joinToString(",") { "'$it'" }})")
            .fetch()
            .getValues(0)
        }
        selectQueries += 1

        cacheData.addAll(
          results.map { mapper.readValue(it as String, DefaultCacheData::class.java) }
        )

        if (!typesWithoutFwdRelationships.contains(type)) {
          val result = getRelationshipPointers(type, chunk, relationshipPrefixes)
          relationshipPointers.addAll(result.data)
          selectQueries += result.selectQueries
        }
      }
      // TODO better error handling
    } catch (e: Exception) {
      log.error("Failed selecting ids for type $type, ids $ids", e)
      return mutableListOf()
    }

    val result = mergeDataAndRelationships(cacheData, relationshipPointers, relationshipPrefixes)

    cacheMetrics.get(
      prefix = name,
      type = type,
      itemCount = result.size,
      requestedSize = ids.size,
      relationshipsRequested = relationshipPrefixes.size,
      selectOperations = selectQueries
    )

    return result
  }

  /**
   * Retrieves the items for the specified type matching the provided identifiers
   *
   * @param type        the type for which to retrieve items
   * @param identifiers the identifiers
   * @return the items matching the type and identifiers
   */
  override fun getAll(type: String, vararg identifiers: String?): MutableCollection<CacheData> {
    val ids = mutableListOf<String>()
    identifiers.forEach { ids.add(it!!) }
    return getAll(type, ids)
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
    return withRetry(RetryCategory.READ) {
      jooq.select(field("id"))
        .from(table(resourceTableName(type)))
        .fetch()
        .intoSet(field("id"), String::class.java)
    }
  }

  /**
   * Filters the supplied list of identifiers to only those that exist in the cache.
   *
   * @param type the type of the item
   * @param identifiers the identifiers for the items
   * @return the list of identifiers that are present in the cache from the provided identifiers
   */
  override fun existingIdentifiers(type: String, identifiers: MutableCollection<String>?): MutableCollection<String> {
    log.info("${javaClass.simpleName} existingIdentifiers type: $type identifiers: $identifiers")
    return mutableListOf()
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

    val sql = if (glob.matches(""".*[\?\[].*""".toRegex())) {
      // TODO replace with a compiled regex
      val filter = glob.replace("?", ".", true).replace("*", ".*").replace("""\.+\*""".toRegex(), ".*")
      "SELECT id FROM ${resourceTableName(type)} WHERE id REGEXP '^$filter\$'"
    } else {
      "SELECT id FROM ${resourceTableName(type)} WHERE id LIKE '${glob.replace('*', '%')}'"
    }
    return try {
      return withRetry(RetryCategory.READ) {
        jooq.fetch(sql).getValues(0, String::class.java)
      }
    } catch (e: Exception) {
      log.error("Failed searching for identifiers type: $type glob: $glob reason: ${e.message}", e)
      mutableSetOf()
    }
  }

  /**
   * Gets a single item from the cache by type and id
   *
   * @param type the type of the item
   * @param id   the id of the item
   * @return the item matching the type and id
   */
  override fun get(type: String, id: String?): CacheData? {
    return get(type, id, null)
  }

  override fun get(type: String, id: String?, cacheFilter: CacheFilter?): CacheData? {
    val result = getAll(type, Arrays.asList<String>(id), cacheFilter)
    return if (result.isEmpty()) {
      null
    } else result.iterator().next() // TODO why?
  }

  override fun evict(type: String, id: String) {
    evictAll(type, listOf(id))
  }

  private fun storeAuthoritative(type: String,
                                 agentHint: String?,
                                 items: MutableCollection<CacheData>,
                                 location: String?,
                                 cleanup: Boolean): StoreResult {
    val result = StoreResult()

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

    items
      .filter { it.id != "_ALL_" }
      .forEach {
        currentIds.add(it.id)
        val keys = it.attributes
          .filter { e -> e.value == null }
          .keys
        keys.forEach { na -> it.attributes.remove(na) }

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

    toStore.chunked(writeBatchSize) { chunk ->
      try {
        val insert = jooq.insertInto(
          table(resourceTableName(type)),
          field("id"),
          field("agent"),
          field("location"),
          field("body_hash"),
          field("body"),
          field("last_updated")
        )

        insert.apply {
          chunk.forEach {
            values(it, agent, location, hashes[it], bodies[it], now)
          }

          onDuplicateKeyUpdate()
            .set(field("body_hash"), MySQLDSL.values(field("body_hash")) as Any)
            .set(field("body"), MySQLDSL.values(field("body")) as Any)
            .set(field("last_updated"), MySQLDSL.values(field("last_updated")) as Any)
        }

        withRetry(RetryCategory.WRITE) {
          insert.execute()
        }
        result.insertQueries.incrementAndGet()
      } catch (e: DataAccessException) {
        log.error("Error inserting ids: $chunk", e)
      } catch (e: SQLDialectNotSupportedException) {
        chunk.forEach {
          val exists = withRetry(RetryCategory.READ) {
            jooq.fetchExists(
              jooq.select()
                .from(resourceTableName(type))
                .where(field("id").eq(it), field("agent").eq(agent))
                .forUpdate()
            )
          }
          result.selectQueries.incrementAndGet()
          if (exists) {
            withRetry(RetryCategory.WRITE) {
              jooq.update(table(resourceTableName(type)))
                .set(field("body_hash"), hashes[it])
                .set(field("body"), bodies[it])
                .set(field("last_updated"), clock.millis())
                .where(field("id").eq(it), field("agent").eq(agent))
                .execute()

            }
            result.updateQueries.incrementAndGet()
            result.itemCount.incrementAndGet()
          } else {
            withRetry(RetryCategory.WRITE) {
              jooq.insertInto(
                table(resourceTableName(type)),
                field("id"),
                field("agent"),
                field("location"),
                field("body_hash"),
                field("body"),
                field("last_updated")
              ).values(
                it,
                agent,
                location,
                hashes[it],
                bodies[it],
                clock.millis()
              ).execute()
            }
            result.insertQueries.incrementAndGet()
            result.itemCount.incrementAndGet()
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

    try {
      toDelete.forEach { id ->
        withRetry(RetryCategory.WRITE) {
          jooq.deleteFrom(table(resourceTableName(type)))
            .where(field("id").eq(id), field("agent").eq(agent))
            .execute()
        }
        result.deleteQueries.incrementAndGet()
      }
    } catch (e: Exception) {
      log.error("Error deleting stale resource", e)
    }

    return result
  }

  private fun storeInformative(type: String, items: MutableCollection<CacheData>, cleanup: Boolean): StoreResult {
    val result = StoreResult()

    val sourceAgents = items.filter { it.relationships.isNotEmpty() }
      .map { it.relationships.keys }
      .flatten()
      .toSet()

    if (sourceAgents.isEmpty()) {
      log.warn("no relationships found for type $type")
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
      .filter { it.id != "_ALL_" }
      .forEach { cacheData ->
        cacheData.relationships.entries.forEach { rels ->
          val relType = rels.key.substringBefore(delimiter = ":", missingDelimiterValue = "")
          rels.value.forEach { r ->
            val fwdKey = "${cacheData.id}|$r"
            val revKey = "$r|${cacheData.id}"
            currentIds.add(fwdKey)
            currentIds.add(revKey)

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

      pointers.chunked(writeBatchSize) { chunk ->
        try {
          val insert = jooq.insertInto(
            table(relTableName(type)),
            field("uuid"),
            field("id"),
            field("rel_id"),
            field("rel_agent"),
            field("rel_type"),
            field("last_updated")
          )

          insert.apply {
            chunk.forEach {
              values(ulid.toString(), it.id, it.rel_id, it.rel_type, relType, now)
              ulid = ULID().nextMonotonicValue(ulid)
            }
          }

          withRetry(RetryCategory.WRITE) {
            insert.execute()
          }
        } catch (e: Exception) {
          log.error("Error inserting forward relationships for $type -> $relType", e)
        }
      }

      pointers.asSequence().filter { newRevRelIds.contains("${it.rel_id}|${it.id}") }
        .chunked(writeBatchSize) { chunk ->
          try {
            val insert = jooq.insertInto(
              table(relTableName(relType)),
              field("uuid"),
              field("id"),
              field("rel_id"),
              field("rel_agent"),
              field("rel_type"),
              field("last_updated")
            )

            insert.apply {
              chunk.forEach {
                values(ulid.toString(), it.rel_id, it.id, it.rel_type, type, now)
                ulid = ULID().nextMonotonicValue(ulid)
              }
            }

            withRetry(RetryCategory.WRITE) {
              insert.execute()
            }
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
            jooq.deleteFrom(table(relTableName(type)))
              .where(field("uuid").eq(it.value))
              .execute()
          }
          result.deleteQueries.incrementAndGet()
        }
        revToDelete.forEach {
          if (oldRevIdsToType.getOrDefault(it.key, "").isNotBlank()) {
            withRetry(RetryCategory.WRITE) {
              jooq.deleteFrom(table(relTableName(oldRevIdsToType[it.key]!!)))
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
        if (jooq.dialect().name != "H2") {
          withRetry(RetryCategory.WRITE) {
            jooq.execute("CREATE TABLE IF NOT EXISTS ${resourceTableName(type)} " +
              "LIKE cats_v${schemaVersion}_resource_template")
            jooq.execute("CREATE TABLE IF NOT EXISTS ${relTableName(type)} " +
              "LIKE cats_v${schemaVersion}_rel_template")
          }

          createdTables.add(type)
        } else {
          withRetry(RetryCategory.WRITE) {
            jooq.execute("CREATE TABLE ${resourceTableName(type)} " +
              "AS SELECT * FROM cats_v${schemaVersion}_resource_template WHERE 1=0")
            jooq.execute("CREATE TABLE ${relTableName(type)} " +
              "AS SELECT * FROM cats_v${schemaVersion}_rel_template WHERE 1=0")
          }

          createdTables.add(type)
        }
      } catch (e: Exception) {
        log.error("Error creating tables for type $type", e)
      }
    }
    if (!createdTables.contains(onDemandType)) {
      // TODO not sure if best schema for onDemand
      try {
        if (jooq.dialect().name != "H2") {
          withRetry(RetryCategory.WRITE) {
            jooq.execute("CREATE TABLE IF NOT EXISTS ${resourceTableName(onDemandType)} " +
              "LIKE cats_v${schemaVersion}_resource_template")
            jooq.execute("CREATE TABLE IF NOT EXISTS ${relTableName(onDemandType)} " +
              "LIKE cats_v${schemaVersion}_rel_template")
          }

          createdTables.add(onDemandType)
        } else {
          withRetry(RetryCategory.WRITE) {
            jooq.execute("CREATE TABLE ${resourceTableName(onDemandType)} " +
              "AS SELECT * FROM cats_v${schemaVersion}_resource_template WHERE 1=0")
            jooq.execute("CREATE TABLE ${relTableName(onDemandType)} " +
              "AS SELECT * FROM cats_v${schemaVersion}_rel_template WHERE 1=0")
          }

          createdTables.add(onDemandType)
        }
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

  private fun resourceTableName(type: String): String =
    "cats_v${schemaVersion}_${if (tablePrefix != null) "${tablePrefix}_" else ""}${sanitizeType(type)}"

  private fun relTableName(type: String): String =
    "cats_v${schemaVersion}_${if (tablePrefix != null) "${tablePrefix}_" else ""}${sanitizeType(type)}_rel"

  private fun sanitizeType(type: String): String {
    return type.replace("""[:/\-]""".toRegex(), "_")
  }

  private fun getHash(body: String?): String? {
    if (body.isNullOrBlank()) {
      return null
    }
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
        .digest(body.toByteArray())
      digest.fold("") { str, it ->
        str + "%02x".format(it)
      }
    } catch (e: Exception) {
      log.error("error calculating hash for body: $body", e)
      null
    }
  }

  private fun getHashIds(type: String, agent: String?): List<HashId> {
    return withRetry(RetryCategory.READ) {
      jooq
        .select(field("body_hash"), field("id"))
        .from(table(resourceTableName(type)))
        .where(
          field("agent").eq(agent)
        )
        .fetch()
        .into(HashId::class.java)
    }
  }

  private fun getRelationshipKeys(type: String, sourceAgent: String): MutableList<RelId> {
    return withRetry(RetryCategory.READ) {
      jooq
        .select(field("uuid"), field("id"), field("rel_id"), field("rel_agent"))
        .from(table(relTableName(type)))
        .where(field("rel_agent").eq(sourceAgent))
        .fetch()
        .into(RelId::class.java)
    }
  }

  private fun getRelationshipKeys(type: String, origType: String, sourceAgent: String): MutableList<RelId> {
    return withRetry(RetryCategory.READ) {
      jooq
        .select(field("uuid"), field("id"), field("rel_id"), field("rel_agent"))
        .from(table(relTableName(type)))
        .where(
          field("rel_agent").eq(sourceAgent),
          field("rel_type").eq(origType)
        )
        .fetch()
        .into(RelId::class.java)
    }
  }

  private fun getRelationshipPointers(
    type: String,
    ids: Collection<String>,
    relationshipPrefixes: List<String>): RelationshipPointersResult {

    val relationshipPointers = mutableListOf<RelPointer>()

    var selectQueries = 0
    ids.chunked(readBatchSize) { chunk ->
      val sql = "ID in (${chunk.joinToString(",") { "'$it'" }})"

      if (relationshipPrefixes.isNotEmpty()) {
        relationshipPointers.addAll(
          if (relationshipPrefixes.contains("ALL")) {
            withRetry(RetryCategory.READ) {
              jooq.select(field("id"), field("rel_id"), field("rel_type"))
                .from(table(relTableName(type)))
                .where(sql)
                .fetch()
                .into(RelPointer::class.java)
            }
          } else {
            val relWhere = " AND ('rel_type' LIKE " +
              relationshipPrefixes.joinToString(" OR 'rel_type' LIKE ") { "'$it%'" } + ")"
            withRetry(RetryCategory.READ) {
              jooq.select(field("id"), field("rel_id"), field("rel_type"))
                .from(table(relTableName(type)))
                .where(sql + relWhere)
                .fetch()
                .into(RelPointer::class.java)
            }
          }
        )
        selectQueries += 1
      }
    }
    return RelationshipPointersResult(relationshipPointers, selectQueries)
  }

  private fun mergeDataAndRelationships(cacheData: Collection<CacheData>,
                                        relationshipPointers: Collection<RelPointer>,
                                        relationshipPrefixes: List<String>): MutableCollection<CacheData> {
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

  private fun normalizeRelationships(rels: Map<String, Collection<String>>,
                                     filterPrefixes: List<String>): Map<String, Collection<String>> {
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

  private enum class RetryCategory {
    WRITE, READ
  }

  private fun <T> withRetry(category: RetryCategory, action: () -> T): T =
    retrySupport.retry(
      action,
      when (category) {
        RetryCategory.READ -> sqlRetryProperties.reads.maxRetries
        RetryCategory.WRITE -> sqlRetryProperties.transactions.maxRetries
      },
      when (category) {
        RetryCategory.READ -> sqlRetryProperties.reads.backoffMs
        RetryCategory.WRITE -> sqlRetryProperties.transactions.backoffMs
      },
      false
    )

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

  private data class RelationshipPointersResult(
    val data: MutableList<RelPointer>,
    val selectQueries: Int
  )

  private inner class StoreResult {
    val itemCount = AtomicInteger(0)
    val selectQueries = AtomicInteger(0)
    val insertQueries = AtomicInteger(0)
    val updateQueries = AtomicInteger(0)
    val deleteQueries = AtomicInteger(0)
  }
}
