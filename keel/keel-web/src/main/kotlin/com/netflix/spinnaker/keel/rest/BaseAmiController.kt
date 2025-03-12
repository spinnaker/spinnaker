package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/base-amis"])
@ConditionalOnProperty("keel.plugins.bakery.enabled")
class BaseAmiController(
  private val baseImageCache: BaseImageCache
) {
  @GetMapping(produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE])
  fun get(): Map<String, Map<String, String>> =
    baseImageCache.allVersions
}
