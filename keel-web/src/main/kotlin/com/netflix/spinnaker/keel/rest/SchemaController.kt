package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.jsonschema.JsonSchemaGenerator
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/schema"])
class SchemaController(
  private val objectMapper: ObjectMapper
) {
  private val generator = JsonSchemaGenerator(objectMapper)

  @GetMapping(
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun manifest(request: HttpServletRequest): Map<String, Any?> {
    val schema = generator
      .generate<DeliveryConfig>(request.requestURL.toString())
    return objectMapper.convertValue(schema)
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
