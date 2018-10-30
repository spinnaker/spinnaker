package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.model.Resource
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/resources"])
class ResourceController() {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @PostMapping(consumes = [APPLICATION_YAML_VALUE], produces = [APPLICATION_YAML_VALUE])
  fun create(@RequestBody resource: Resource): Resource {
    log.info("Creating: $resource")
    return resource
  }
}
