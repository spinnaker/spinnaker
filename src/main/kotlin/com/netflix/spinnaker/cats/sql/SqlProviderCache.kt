package com.netflix.spinnaker.cats.sql

import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.cats.sql.cache.SqlCache
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class SqlProviderCache(private val backingStore: WriteableCache) : ProviderCache {

  companion object {
    private const val ALL_ID = "_ALL_" // this implementation ignores this entirely
    private val log = LoggerFactory.getLogger(javaClass)
  }

  init {
    if (backingStore !is SqlCache) {
      throw IllegalStateException("SqlProviderCache must be wired with a SqlCache backingStore")
    }
  }

  /**
   * Filters the supplied list of identifiers to only those that exist in the cache.
   *
   * @param type the type of the item
   * @param identifiers the identifiers for the items
   * @return the list of identifiers that are present in the cache from the provided identifiers
   */
  override fun existingIdentifiers(type: String, identifiers: MutableCollection<String>): MutableCollection<String> {
    return backingStore.existingIdentifiers(type, identifiers)
  }

  /**
   * Returns the identifiers for the specified type that match the provided glob.
   *
   * @param type The type for which to retrieve identifiers
   * @param glob The glob to match against the identifiers
   * @return the identifiers for the type that match the glob
   */
  override fun filterIdentifiers(type: String?, glob: String?): MutableCollection<String> {
    return backingStore.filterIdentifiers(type, glob)
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

  override fun getAll(type: String, identifiers: MutableCollection<String>?): MutableCollection<CacheData> {
    return getAll(type, identifiers, null)
  }

  override fun getAll(type: String, cacheFilter: CacheFilter?): MutableCollection<CacheData> {
    validateTypes(type)
    return backingStore.getAll(type, cacheFilter)
  }

  override fun getAll(type: String, identifiers: MutableCollection<String>?, cacheFilter: CacheFilter?): MutableCollection<CacheData> {
    validateTypes(type)
    return backingStore.getAll(type, identifiers, cacheFilter)
  }

  /**
   * Retrieves the items for the specified type matching the provided identifiers
   *
   * @param type        the type for which to retrieve items
   * @param identifiers the identifiers
   * @return the items matching the type and identifiers
   */
  override fun getAll(type: String, vararg identifiers: String): MutableCollection<CacheData> {
    return getAll(type, identifiers.toMutableList())
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
    if (ALL_ID == id) {
      log.warn("Unexpected request for $ALL_ID for type: $type, cacheFilter: $cacheFilter")
      return null
    }
    validateTypes(type)

    return backingStore.get(type, id, cacheFilter) ?: return null
  }

  override fun evictDeletedItems(type: String, ids: Collection<String>) {
    MDC.put("agentClass", "evictDeletedItems")

    backingStore.evictAll(type, ids)
  }

  /**
   * Retrieves all the identifiers for a type
   *
   * @param type the type for which to retrieve identifiers
   * @return the identifiers for the type
   */
  override fun getIdentifiers(type: String): MutableCollection<String> {
    validateTypes(type)
    return backingStore.getIdentifiers(type)
  }

  override fun putCacheResult(source: String,
                              authoritativeTypes: MutableCollection<String>,
                              cacheResult: CacheResult) {
    MDC.put("agentClass", "$source putCacheResult")

    // TODO every source type should have an authoritative agent and every agent should be authoritative for something
    // TODO terrible hack because no AWS agent is authoritative for clusters, fix in ClusterCachingAgent
    // TODO same with namedImages - fix in AWS ImageCachingAgent
    if (
      source.contains("clustercaching", ignoreCase = true) &&
      !authoritativeTypes.contains(CLUSTERS.ns) &&
      cacheResult.cacheResults
        .any {
          it.key.startsWith(CLUSTERS.ns)
        }
    ) {
      authoritativeTypes.add(CLUSTERS.ns)
    } else if (
      source.contains("imagecaching", ignoreCase = true) &&
      cacheResult.cacheResults
        .any {
          it.key.startsWith(NAMED_IMAGES.ns)
        }
    ) {
      authoritativeTypes.add(NAMED_IMAGES.ns)
    }

    cacheResult.cacheResults
      .filter {
        it.key.contains(ON_DEMAND.ns, ignoreCase = true)
      }
      .forEach {
        authoritativeTypes.add(it.key)
      }

    val cachedTypes = mutableSetOf<String>()
    // Update resource table from Authoritative sources only
    when {
      // OnDemand agents should only be treated as authoritative and don't use standard eviction logic
      source.contains(ON_DEMAND.ns, ignoreCase = true) -> cacheResult.cacheResults
        // And OnDemand agents shouldn't update other resource type tables
        .filter {
          it.key.contains(ON_DEMAND.ns, ignoreCase = true)
        }
        .forEach {
          cacheDataType(it.key, source, it.value, authoritative = true, cleanup = false)
        }
      authoritativeTypes.isNotEmpty() -> cacheResult.cacheResults
        .filter {
          authoritativeTypes.contains(it.key)
        }
        .forEach {
          cacheDataType(it.key, source, it.value, authoritative = true)
          cachedTypes.add(it.key)
        }
      else -> // If there are no authoritative types in cacheResult, override all as authoritative without cleanup
        cacheResult.cacheResults
          .forEach {
            cacheDataType(it.key, source, it.value, authoritative = true, cleanup = false)
            cachedTypes.add(it.key)
          }
    }

    // Update relationships for non-authoritative types
    if (!source.contains(ON_DEMAND.ns, ignoreCase = true)) {
      cacheResult.cacheResults
        .filter {
          !cachedTypes.contains(it.key)
        }
        .forEach {
          cacheDataType(it.key, source, it.value, authoritative = false)
        }
    }

    if (cacheResult.evictions.isNotEmpty()) {
      cacheResult.evictions.forEach {
        evictDeletedItems(it.key, it.value)
      }
    }
  }

  override fun addCacheResult(source: String,
                              authoritativeTypes: MutableCollection<String>,
                              cacheResult: CacheResult): Unit {
    MDC.put("agentClass", "$source putCacheResult")

    val cachedTypes = mutableSetOf<String>()

    if (authoritativeTypes.isNotEmpty()) {
      cacheResult.cacheResults
        .filter {
          authoritativeTypes.contains(it.key)
        }
        .forEach {
          cacheDataType(it.key, source, it.value, authoritative = true, cleanup = false)
          cachedTypes.add(it.key)
        }
    }

    cacheResult.cacheResults
      .filter { !cachedTypes.contains(it.key) }
      .forEach {
        cacheDataType(it.key, source, it.value, authoritative = false, cleanup = false)
      }
  }

  override fun putCacheData(type: String, cacheData: CacheData) {
    MDC.put("agentClass", "putCacheData")
    backingStore.merge(type, cacheData)
  }

  private fun validateTypes(type: String) {
    validateTypes(listOf(type))
  }

  private fun validateTypes(types: List<String>) {
    val invalid = types
      .asSequence()
      .filter { it.contains(":") }
      .toSet()

    if (invalid.isNotEmpty()) {
      throw IllegalArgumentException("Invalid types: $invalid")
    }
  }

  private fun cacheDataType(type: String, agent: String, items: Collection<CacheData>, authoritative: Boolean) {
    cacheDataType(type, agent, items, authoritative, cleanup = true)
  }

  private fun cacheDataType(type: String,
                            agent: String,
                            items: Collection<CacheData>,
                            authoritative: Boolean,
                            cleanup: Boolean) {
    val toStore = ArrayList<CacheData>(items.size + 1)
    items.forEach {
      toStore.add(uniqueifyRelationships(it, agent))
    }

    // OnDemand agents are always updated incrementally and should not trigger auto-cleanup at the WriteableCache layer
    val cleanupOverride =
      if (agent.contains(ON_DEMAND.ns, ignoreCase = true) || type.contains(ON_DEMAND.ns, ignoreCase = true)) {
        false
      } else {
        cleanup
      }

    (backingStore as SqlCache).mergeAll(type, agent, toStore, authoritative, cleanupOverride)
  }

  private fun uniqueifyRelationships(source: CacheData, sourceAgentType: String): CacheData {
    val relationships = HashMap<String, Collection<String>>(source.relationships.size)
    for ((key, value) in source.relationships) {
      relationships["$key:$sourceAgentType"] = value
    }
    return DefaultCacheData(source.id, source.ttlSeconds, source.attributes, relationships)
  }
}
