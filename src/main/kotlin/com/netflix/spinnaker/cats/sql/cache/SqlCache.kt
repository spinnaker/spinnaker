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
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.RESERVATION_REPORTS
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.RESERVED_INSTANCES
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Clock
import java.util.Arrays
import java.util.concurrent.ConcurrentSkipListSet

class SqlCache(
  private val name: String,
  private val jooq: DSLContext,
  private val mapper: ObjectMapper,
  private val clock: Clock,
  private val sqlRetryProperties: SqlRetryProperties, // TODO use this
  private val prefix: String?,
  private val batchSize: Int
) : WriteableCache {

  private val schema_version = 1 // TODO: move somewhere sane
  private val relationshipKeyRegex = """^(?:\w+:)?(?:[\w+\-]+)\/([\w+\-]+)\/.*$""".toRegex()
  private val onDemandType = "onDemand"

  // TODO: this may be incorrect for !aws and definitely shouldn't be a hand maintained list here
  private val typesWithoutFwdRelationships = setOf<String>(
    CLUSTERS.ns, CERTIFICATES.ns, NAMED_IMAGES.ns, RESERVATION_REPORTS.ns, RESERVED_INSTANCES.ns,
    "elasticIps", "instanceTypes", "keyPairs", "securityGroups", "subnets", "taggedImage"
  )

  private val sqlChunkSize = batchSize

  private val log = LoggerFactory.getLogger(javaClass)

  private var createdTables = ConcurrentSkipListSet<String>()

  init {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun evictAll(type: String, ids: Collection<String>) {
    // TODO: this only supports deleting resource records, not relationships. Both are primarily deleted
    // TODO: by caching agents themselves. In both cases, this behavior may be incompatible with the
    // TODO: titus streaming agent. Need to verify and potentially customize for this cache implementation.
    log.debug("${javaClass.simpleName} evictAll type: ${type} ids: ${ids}")

    try {
      ids.chunked(sqlChunkSize) { chunk ->
        jooq.deleteFrom(table(resourceTableName(type)))
          .where("id in (${chunk.joinToString(",") { "'$it'" }})")
          .execute()
      }
    } catch (e: Exception) {
      log.error("error evicting records", e)
    }
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

    if (authoritative) {
      storeAuthoritative(type, agent, items!!, location, cleanup)
    } else {
      storeInformative(type, items, cleanup)
    }
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
    val cacheData = mutableListOf<CacheData>()
    try {
      cacheData.addAll(
        jooq.select(field("body"))
          .from(table(resourceTableName(type)))
          .fetch()
          .getValues(0)
          .asSequence()
          .map { mapper.readValue(it as String, DefaultCacheData::class.java) }
          .toList()
      )
    } catch (e: Exception) {
      log.error("Failed selecting ids for type $type", e)
      return mutableListOf()
    }


    if (typesWithoutFwdRelationships.contains(type)) {
      return cacheData
    }

    val relationshipPrefixes = getRelationshipFilterPrefixes(cacheFilter)
    val relationshipPointers = getRelationshipPointers(
      type,
      cacheData.asSequence().map { it.id }.toMutableList(),
      relationshipPrefixes
    )

    return mergeDataAndRelationships(cacheData, relationshipPointers, relationshipPrefixes)
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
      return mutableListOf()
    }

    val cacheData = mutableListOf<CacheData>()
    val relationshipPointers = mutableListOf<RelPointer>()
    val relationshipPrefixes = getRelationshipFilterPrefixes(cacheFilter)

    try {
      //TODO make configurable
      ids.chunked(sqlChunkSize) { chunk ->
        val results = jooq.select(field("body"))
          .from(table(resourceTableName(type)))
          .where("ID in (${chunk.joinToString(",") { "'$it'" }})")
          .fetch()
          .getValues(0)

        cacheData.addAll(
          results.map { mapper.readValue(it as String, DefaultCacheData::class.java) }
        )

        if (!typesWithoutFwdRelationships.contains(type)) {
          relationshipPointers.addAll(
            getRelationshipPointers(type, chunk, relationshipPrefixes)
          )
        }
      }
      // TODO better error handling
    } catch (e: Exception) {
      return mutableListOf()
    }

    return mergeDataAndRelationships(cacheData, relationshipPointers, relationshipPrefixes)
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
//    mergeAll(type, mutableListOf(cacheData))
    mergeAll(type, null, mutableListOf(cacheData), true, false)
  }

  /**
   * Retrieves all the identifiers for a type
   *
   * @param type the type for which to retrieve identifiers
   * @return the identifiers for the type
   */
  override fun getIdentifiers(type: String): MutableCollection<String> {
    return jooq.select(field("id"))
      .from(table(resourceTableName(type)))
      .fetch()
      .intoSet(field("id"), String::class.java)
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
    log.info("${javaClass.simpleName} filterIdentifiers type: $type glob: $glob")

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
      return jooq
        .fetch(sql)
        .getValues(0, String::class.java)
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
                                 cleanup: Boolean) {
    val agent = agentHint ?: "unknown"
    val existingHashIds = getHashIds(type, agent)
    val existingHashes = existingHashIds
      .asSequence()
      .map { it.body_hash }
      .toSet()
    val existingIds = existingHashIds
      .asSequence()
      .map { it.id }
      .toSet()
    val currentIds = mutableSetOf<String>()

    items
      .filter { it.id != "_ALL_" }
      .forEach {
        currentIds.add(it.id)
        val keys = it.attributes
          .filter { e -> e.value == null }
          .keys
        keys.forEach { na -> it.attributes.remove(na) }
        val body: String? = mapper.writeValueAsString(it)
        val bodyHash = getHash(body)
        val skip = if (bodyHash == null) {
          true
        } else {
          existingHashes.contains(bodyHash)
        }

        if (!skip) {
          try {
            jooq.insertInto(
              table(resourceTableName(type)),
              field("id"),
              field("agent"),
              field("location"),
              field("body_hash"),
              field("body"),
              field("last_updated")
            ).values(
              it.id,
              agent,
              location,
              bodyHash,
              body,
              clock.millis()
            ).onDuplicateKeyUpdate()
              .set(field("body_hash"), bodyHash)
              .set(field("body"), body)
              .set(field("last_updated"), clock.millis())
              .execute()
          } catch (e: DataAccessException) {
            log.error("Error inserting id: ${it.id}", e)
          } catch (e: SQLDialectNotSupportedException) {
            val exists = jooq.fetchExists(
              jooq.select()
                .from(resourceTableName(type))
                .where(field("id").eq(it.id), field("agent").eq(agent))
                .forUpdate()
            )
            if (exists) {
              jooq.update(table(resourceTableName(type)))
                .set(field("body_hash"), bodyHash)
                .set(field("body"), body)
                .set(field("last_updated"), clock.millis())
                .where(field("id").eq(it.id), field("agent").eq(agent))
                .execute()
            } else {
              jooq.insertInto(
                table(resourceTableName(type)),
                field("id"),
                field("agent"),
                field("location"),
                field("body_hash"),
                field("body"),
                field("last_updated")
              ).values(
                it.id,
                agent,
                location,
                bodyHash,
                body,
                clock.millis()
              ).execute()
            }
          }
        }
      }

    if (!cleanup) {
      return
    }

    val toDelete = existingIds
      .asSequence()
      .filter { !currentIds.contains(it) }
      .toSet()

    try {
      toDelete.forEach { id ->
        jooq.deleteFrom(table(resourceTableName(type)))
          .where(field("id").eq(id))
          .execute()
      }
    } catch (e: Exception) {
      log.error("Error deleting stale resource", e)
    }
  }

  private fun storeInformative(type: String, items: MutableCollection<CacheData>, cleanup: Boolean) {
    val sourceAgents = items.filter { it.relationships.isNotEmpty() }
      .map { it.relationships.keys }
      .flatten()
      .toSet()

    if (sourceAgents.isEmpty()) {
      log.warn("no relationships found for type $type")
      return
    }

    val existingFwdRelIds = sourceAgents.map { getRelationshipKeys(type, it) }
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

    val existingRevRelIds = existingRevRelTypes.map { relType ->
      sourceAgents.map { agent ->
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
              try {
                jooq.insertInto(
                  table(relTableName(type)),
                  field("uuid"),
                  field("id"),
                  field("rel_id"),
                  field("rel_agent"),
                  field("rel_type"),
                  field("last_updated")
                ).values(
                  ULID().nextULID(),
                  cacheData.id,
                  r,
                  rels.key,
                  relType,
                  clock.millis()
                ).execute()
              } catch (e: Exception) {
                log.error("Error inserting relationship ${cacheData.id} -> $r ${e.message}", e)
              }
            }

            if (!oldRevIds.contains(revKey)) {
              try {
                jooq.insertInto(
                  table(relTableName(relType)),
                  field("uuid"),
                  field("id"),
                  field("rel_id"),
                  field("rel_agent"),
                  field("rel_type"),
                  field("last_updated")
                ).values(
                  ULID().nextULID(),
                  r,
                  cacheData.id,
                  rels.key,
                  type,
                  clock.millis()
                ).execute()
              } catch (e: Exception) {
                log.error("Error inserting $r -> ${cacheData.id} ${e.message}", e)
              }
            }
          }
        }
      }

    if (!cleanup) {
      return
    }

    val fwdToDelete = oldFwdIds.filter { !currentIds.contains(it.key) }
    val revToDelete = oldRevIds.filter { !currentIds.contains(it.key) }

    if (fwdToDelete.isNotEmpty() || revToDelete.isNotEmpty()) {
      try {
        fwdToDelete.forEach {
          jooq.deleteFrom(table(relTableName(type)))
            .where(field("uuid").eq(it.value))
            .execute()
        }
        revToDelete.forEach {
          if (oldRevIdsToType.getOrDefault(it.key, "").isNotBlank()) {
            jooq.deleteFrom(table(relTableName(oldRevIdsToType[it.key]!!)))
              .where(field("uuid").eq(it.value))
              .execute()
          } else {
            log.warn("Couldn't delete ${it.key}, no mapping to type")
          }
        }
      } catch (e: Exception) {
        log.error("Error deleting stale relationships", e)
      }
    }
  }

  private fun createTables(type: String) {
    if (!createdTables.contains(type)) {
      try {
        if (jooq.dialect().name != "H2") {
          jooq.execute("CREATE TABLE IF NOT EXISTS ${resourceTableName(type)} " +
            "LIKE cats_v${schema_version}_resource_template")
          jooq.execute("CREATE TABLE IF NOT EXISTS ${relTableName(type)} " +
            "LIKE cats_v${schema_version}_rel_template")

          createdTables.add(type)
        } else {
          jooq.execute("CREATE TABLE ${resourceTableName(type)} " +
            "AS SELECT * FROM cats_v${schema_version}_resource_template WHERE 1=0")
          jooq.execute("CREATE TABLE ${relTableName(type)} " +
            "AS SELECT * FROM cats_v${schema_version}_rel_template WHERE 1=0")

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
          jooq.execute("CREATE TABLE IF NOT EXISTS ${resourceTableName(onDemandType)} " +
            "LIKE cats_v${schema_version}_resource_template")
          jooq.execute("CREATE TABLE IF NOT EXISTS ${relTableName(onDemandType)} " +
            "LIKE cats_v${schema_version}_rel_template")

          createdTables.add(onDemandType)
        } else {
          jooq.execute("CREATE TABLE ${resourceTableName(onDemandType)} " +
            "AS SELECT * FROM cats_v${schema_version}_resource_template WHERE 1=0")
          jooq.execute("CREATE TABLE ${relTableName(onDemandType)} " +
            "AS SELECT * FROM cats_v${schema_version}_rel_template WHERE 1=0")

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
    "cats_v${schema_version}_${if (prefix != null) "${prefix}_" else ""}${sanitizeType(type)}"

  private fun relTableName(type: String): String =
    "cats_v${schema_version}_${if (prefix != null) "${prefix}_" else ""}${sanitizeType(type)}_rel"

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
    return jooq
      .select(field("body_hash"), field("id"))
      .from(table(resourceTableName(type)))
      .where(
        field("agent").eq(agent)
      )
      .fetch()
      .into(HashId::class.java)
  }

  private fun getRelationshipKeys(type: String, sourceAgent: String): MutableList<RelId> {
    return jooq
      .select(field("uuid"), field("id"), field("rel_id"), field("rel_agent"))
      .from(table(relTableName(type)))
      .where(field("rel_agent").eq(sourceAgent))
      .fetch()
      .into(RelId::class.java)
  }

  private fun getRelationshipKeys(type: String, origType: String, sourceAgent: String): MutableList<RelId> {
    return jooq
      .select(field("uuid"), field("id"), field("rel_id"), field("rel_agent"))
      .from(table(relTableName(type)))
      .where(
        field("rel_agent").eq(sourceAgent),
        field("rel_type").eq(origType)
      )
      .fetch()
      .into(RelId::class.java)
  }

  private fun getRelationshipPointers(
    type: String,
    ids: Collection<String>,
    relationshipPrefixes: List<String>): MutableList<RelPointer> {

    val relationshipPointers = mutableListOf<RelPointer>()

    ids.chunked(sqlChunkSize) { chunk ->
      val sql = "ID in (${chunk.joinToString(",") { "'$it'" }})"

      if (relationshipPrefixes.isNotEmpty()) {
        relationshipPointers.addAll(
          if (relationshipPrefixes.contains("ALL")) {
            jooq.select(field("id"), field("rel_id"), field("rel_type"))
              .from(table(relTableName(type)))
              .where(sql)
              .fetch()
              .into(RelPointer::class.java)
          } else {
            val relWhere = " AND ('rel_type' LIKE " +
              relationshipPrefixes.joinToString(" OR 'rel_type' LIKE ") { "'${it}%'" } + ")"
            jooq.select(field("id"), field("rel_id"), field("rel_type"))
              .from(table(relTableName(type)))
              .where(sql + relWhere)
              .fetch()
              .into(RelPointer::class.java)
          }
        )
      }
    }
    return relationshipPointers
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
          data[item.id]!!.relationships.putAll(normalizeRelationships(item.relationships, relationshipPrefixes))
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
                // TODO can relationships = Map<String, Set<String>> instead of String to Collection?
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

}
