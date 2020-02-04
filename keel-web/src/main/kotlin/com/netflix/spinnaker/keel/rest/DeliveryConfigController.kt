package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.EnvironmentArtifactsSummary
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.diff.AdHocDiffer
import com.netflix.spinnaker.keel.diff.EnvironmentDiff
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository.Companion.DEFAULT_MAX_EVENTS
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/delivery-configs"])
class DeliveryConfigController(
  private val resourcePersister: ResourcePersister,
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val artifactRepository: ArtifactRepository,
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

  @DeleteMapping(
    path = ["/{name}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun delete(@PathVariable("name") name: String): DeliveryConfig {
    val deliveryConfig = deliveryConfigRepository.get(name)
    log.info("Deleting delivery config $name: $deliveryConfig")
    resourcePersister.deleteDeliveryConfig(name)
    return deliveryConfig
  }

  // todo eb: make this work with artifact references
  @PostMapping(
    path = ["/diff"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun diff(@RequestBody deliveryConfig: SubmittedDeliveryConfig): List<EnvironmentDiff> =
    adHocDiffer.calculate(deliveryConfig)

  @GetMapping(
    path = ["/{name}/artifacts"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun getArtifacts(
    @PathVariable("name") name: String
  ): List<EnvironmentArtifactsSummary> =
    deliveryConfigRepository.get(name).let {
      artifactRepository.versionsByEnvironment(it)
    }

  @GetMapping(
    path = ["/{name}/environment/{environment}/constraints"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun getConstraintState(
    @PathVariable("name") name: String,
    @PathVariable("environment") environment: String,
    @RequestParam("limit") limit: Int?
  ): List<ConstraintState> =
    deliveryConfigRepository.constraintStateFor(name, environment, limit ?: DEFAULT_MAX_EVENTS)

  @PostMapping(
    path = ["/{name}/environment/{environment}/constraint"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  // TODO: This should be validated against write access to a service account. Service accounts should
  //  become a top-level property of either delivery configs or environments.
  fun updateConstraintStatus(
    @RequestHeader("X-SPINNAKER-USER") user: String,
    @PathVariable("name") name: String,
    @PathVariable("environment") environment: String,
    @RequestBody status: UpdatedConstraintStatus
  ) {
    val currentState = deliveryConfigRepository.getConstraintState(
      name,
      environment,
      status.artifactVersion,
      status.type) ?: throw InvalidConstraintException(
      "$name/$environment/${status.type}/${status.artifactVersion}", "constraint not found")

    deliveryConfigRepository.storeConstraintState(
      currentState.copy(
        status = status.status,
        comment = status.comment ?: currentState.comment,
        judgedAt = Instant.now(),
        judgedBy = user))
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
