package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/kinds"])
class KindController(
  val plugins: List<ResourceHandler<*, *>>
) {
  @GetMapping(produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE])
  fun get(): List<Map<String, Any>> =
    plugins
      .groupBy { it.supportedKind.apiVersion }
      .map { (apiVersion, plugins) ->
        mapOf(
          "api-version" to apiVersion,
          "kinds" to plugins.map { it.supportedKind.kind }
        )
      }
}
