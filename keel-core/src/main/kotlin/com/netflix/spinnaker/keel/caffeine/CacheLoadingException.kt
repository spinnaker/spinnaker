package com.netflix.spinnaker.keel.caffeine

import com.netflix.spinnaker.kork.exceptions.IntegrationException

class CacheLoadingException(cacheName: String, key: Any, cause: Throwable) :
  IntegrationException("Error loading $cacheName cache from key $key", cause)

class BulkCacheLoadingException(cacheName: String, cause: Throwable) :
  IntegrationException("Error loading $cacheName cache", cause)
