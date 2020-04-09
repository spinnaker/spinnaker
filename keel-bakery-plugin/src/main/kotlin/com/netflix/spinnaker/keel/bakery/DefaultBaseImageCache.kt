package com.netflix.spinnaker.keel.bakery

import com.netflix.spinnaker.keel.api.artifacts.BaseLabel
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @param baseImages a map of OS to label -> base AMI version. For example
 * ```
 * xenial:
 *   CANDIDATE: nflx-base-5.530.0-h1663.61bf5c1
 *   RELEASE: nflx-base-5.523.0-h1645.61bf5c1
 * bionic:
 *   CANDIDATE: nflx-base-5.530.0-h1663.61bf5c1
 *   RELEASE: nflx-base-5.523.0-h1645.61bf5c1
 * ```
 */
class DefaultBaseImageCache(
  private val baseImages: Map<String, Map<String, String>>
) : BaseImageCache {
  override fun getBaseAmiVersion(os: String, label: BaseLabel) =
    baseImages[os]?.get(label.name.toLowerCase()) ?: throw UnknownBaseImage(os, label)
}

@ConfigurationProperties(prefix = "keel.plugins.bakery")
class BaseImageCacheProperties {
  var baseImages: Map<String, Map<String, String>> = emptyMap()
}
