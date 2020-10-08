package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuration for the caches in [com.netflix.spinnaker.keel.clouddriver.MemoryCloudDriverCache].
 */
@ConfigurationProperties(prefix = "keel.clouddriver")
class CacheProperties {
  var caches: Map<String, CacheSettings> = emptyMap()

  class CacheSettings(
    var maximumSize: Long? = null,
    var expireAfterWrite: Duration? = null
  )
}
