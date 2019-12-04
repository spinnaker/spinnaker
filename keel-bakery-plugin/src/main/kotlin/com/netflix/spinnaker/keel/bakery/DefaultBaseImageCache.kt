package com.netflix.spinnaker.keel.bakery

import com.netflix.spinnaker.keel.bakery.api.BaseLabel
import org.springframework.boot.context.properties.ConfigurationProperties

class DefaultBaseImageCache(
  private val baseImages: Map<String, Map<String, String>>
) : BaseImageCache {
  override fun getBaseImage(os: String, label: BaseLabel) =
    baseImages[os]?.get(label.name.toLowerCase()) ?: throw UnknownBaseImage(os, label)
}

@ConfigurationProperties("keel.plugins.bakery.base-images")
class BaseImageCacheProperties {
  var baseImages: Map<String, Map<String, String>> = emptyMap()
}
