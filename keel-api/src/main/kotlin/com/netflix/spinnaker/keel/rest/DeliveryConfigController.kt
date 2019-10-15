package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.diff.AdHocDiffer
import com.netflix.spinnaker.keel.diff.EnvironmentDiff
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
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val adHocDiffer: AdHocDiffer
) {
  @PostMapping(
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun upsert(@RequestBody deliveryConfig: SubmittedDeliveryConfig): DeliveryConfig =
    resourcePersister.upsert(deliveryConfig)

  @GetMapping(
    path = ["/{name}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun get(@PathVariable("name") name: String): DeliveryConfig =
    deliveryConfigRepository.get(name)

  @PostMapping(
    path = ["/diff"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun diff(@RequestBody deliveryConfig: SubmittedDeliveryConfig): List<EnvironmentDiff> =
    adHocDiffer.calculate(deliveryConfig)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
