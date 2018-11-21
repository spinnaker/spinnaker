package com.netflix.spinnaker.cats.sql

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SqlProviderRegistry(
  private val providerList: Collection<Provider>,
  private val cacheFactory: NamedCacheFactory
) : ProviderRegistry {
  private val providerCaches = ConcurrentHashMap<String, ProviderCache>()

  init {
    providerList.forEach {
      providerCaches[it.providerName] = SqlProviderCache(cacheFactory.getCache(it.providerName))
    }
  }

  override fun getProviderCache(providerName: String?): ProviderCache? {
    return providerCaches[providerName]
  }

  override fun getProviderCaches(): Collection<Cache> {
    // TODO unwind CompositeCache - there is only one sql cache
    return listOf(providerCaches.values.first())
  }

  override fun getProviders(): Collection<Provider> {
    return providerList
  }
}
