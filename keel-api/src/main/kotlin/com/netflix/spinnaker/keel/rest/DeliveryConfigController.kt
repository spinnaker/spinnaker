package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/delivery-configs"])
class DeliveryConfigController(
  private val resourcePersister: ResourcePersister,
  private val deliveryConfigRepository: DeliveryConfigRepository
) {
  @PostMapping(
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun upsert(@RequestBody deliveryConfig: SubmittedDeliveryConfig): DeliveryConfig =
    DeliveryConfig(
      name = deliveryConfig.name,
      application = deliveryConfig.application,
      artifacts = deliveryConfig.artifacts,
      environments = deliveryConfig.environments.map { env ->
        Environment(
          name = env.name,
          resources = env.resources.map { resource ->
            resourcePersister.upsert(resource)
          }.toSet()
        )
      }.toSet()
    )
      .also {
        deliveryConfigRepository.store(it)
      }

  @GetMapping(
    path = ["/{name}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun get(@PathVariable("name") name: String): DeliveryConfig =
    deliveryConfigRepository.get(name)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

data class SubmittedDeliveryConfig(
  val name: String,
  val application: String,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<SubmittedEnvironment> = emptySet()
)

data class SubmittedEnvironment(
  val name: String,
  val resources: Set<SubmittedResource<Any>>
)
