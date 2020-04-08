package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.diff.AdHocDiffer
import com.netflix.spinnaker.keel.diff.EnvironmentDiff
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/delivery-configs"])
class DeliveryConfigController(
  private val repository: KeelRepository,
  private val adHocDiffer: AdHocDiffer
) {
  @Operation(
    description = "Registers or updates a delivery config manifest."
  )
  @PostMapping(
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('WRITE', 'APPLICATION', #deliveryConfig.application)
    and @authorizationSupport.hasServiceAccountAccess(#deliveryConfig.serviceAccount)"""
  )
  fun upsert(
    @RequestBody
    @SwaggerRequestBody(
      description = "The delivery config. If its `name` matches an existing delivery config the operation is an update, otherwise a new delivery config is created."
    )
    deliveryConfig: SubmittedDeliveryConfig
  ): DeliveryConfig =
    repository.upsertDeliveryConfig(deliveryConfig)

  @GetMapping(
    path = ["/{name}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('READ', 'DELIVERY_CONFIG', #name)
    and @authorizationSupport.hasCloudAccountPermission('READ', 'DELIVERY_CONFIG', #name)"""
  )
  fun get(@PathVariable("name") name: String): DeliveryConfig =
    repository.getDeliveryConfig(name)

  @DeleteMapping(
    path = ["/{name}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('WRITE', 'DELIVERY_CONFIG', #name)
    and @authorizationSupport.hasServiceAccountAccess('DELIVERY_CONFIG', #name)"""
  )
  fun delete(@PathVariable("name") name: String): DeliveryConfig {
    val deliveryConfig = repository.getDeliveryConfig(name)
    log.info("Deleting delivery config $name: $deliveryConfig")
    repository.deleteDeliveryConfig(name)
    return deliveryConfig
  }

  // todo eb: make this work with artifact references
  @PostMapping(
    path = ["/diff"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("@authorizationSupport.hasApplicationPermission('READ', 'DELIVERY_CONFIG', #deliveryConfig.name)")
  fun diff(@RequestBody deliveryConfig: SubmittedDeliveryConfig): List<EnvironmentDiff> =
    adHocDiffer.calculate(deliveryConfig)

  @PostMapping(
    path = ["/validate"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("@authorizationSupport.hasApplicationPermission('READ', 'DELIVERY_CONFIG', #deliveryConfig.name)")
  fun validate(@RequestBody deliveryConfig: SubmittedDeliveryConfig) =
  // TODO: replace with JSON schema/OpenAPI spec validation when ready (for now, leveraging parsing error handling
    //  in [ExceptionHandler])
    mapOf("status" to "valid")

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
