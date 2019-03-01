package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/kinds"])
class KindController(val plugins: List<ResourceHandler<*>>) {
  @GetMapping(produces = [APPLICATION_YAML_VALUE, APPLICATION_JSON_VALUE])
  fun get(): List<Map<String, Any>> =
    plugins
      .groupBy { it.apiVersion }
      .map { (apiVersion, plugin) ->
        mapOf(
          "api-version" to apiVersion,
          "kinds" to plugin.map { it.supportedKind.first.singular }
        )
      }
}
