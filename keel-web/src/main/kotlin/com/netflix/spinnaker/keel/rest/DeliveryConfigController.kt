package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.diff.AdHocDiffer
import com.netflix.spinnaker.keel.diff.EnvironmentDiff
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.WRITE
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.APPLICATION
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.services.DeliveryConfigImporter
import com.netflix.spinnaker.keel.validators.DeliveryConfigProcessor
import com.netflix.spinnaker.keel.validators.DeliveryConfigValidator
import com.netflix.spinnaker.keel.validators.applyAll
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/delivery-configs"])
class DeliveryConfigController(
  private val repository: KeelRepository,
  private val adHocDiffer: AdHocDiffer,
  private val validator: DeliveryConfigValidator,
  private val importer: DeliveryConfigImporter,
  private val authorizationSupport: AuthorizationSupport,
  @Autowired(required = false) private val deliveryConfigProcessors: List<DeliveryConfigProcessor> = emptyList()
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

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
    deliveryConfigProcessors.applyAll(deliveryConfig)
      .let { processedDeliveryConfig ->
        validator.validate(processedDeliveryConfig)
        log.debug("Upserting delivery config '${processedDeliveryConfig.name}' for app '${processedDeliveryConfig.application}'")
        repository.upsertDeliveryConfig(processedDeliveryConfig)
      }

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
  fun delete(@PathVariable("name") name: String) {
    log.debug("Deleting delivery config $name")
    repository.deleteDeliveryConfigByName(name)
  }

  // todo eb: make this work with artifact references
  @PostMapping(
    path = ["/diff"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("@authorizationSupport.hasApplicationPermission('READ', 'APPLICATION', #deliveryConfig.application)")
  fun diff(@RequestBody deliveryConfig: SubmittedDeliveryConfig): List<EnvironmentDiff> {
    validator.validate(deliveryConfig)
    return adHocDiffer.calculate(deliveryConfig)
  }

  @PostMapping(
    path = ["/validate"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  @PreAuthorize("@authorizationSupport.hasApplicationPermission('READ', 'APPLICATION', #deliveryConfig.application)")
  fun validate(@RequestBody deliveryConfig: SubmittedDeliveryConfig) {
    validator.validate(deliveryConfig)
  }

  @Operation(
    description = "Imports a delivery config manifest from source control."
  )
  @PostMapping(
    path = ["/import"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun import(
    @RequestParam repoType: String,
    @RequestParam projectKey: String,
    @RequestParam repoSlug: String,
    @RequestParam manifestPath: String,
    @RequestParam ref: String?
  ): DeliveryConfig {
    val deliveryConfig =
      importer.import(repoType, projectKey, repoSlug, manifestPath, ref ?: "refs/heads/master")

    authorizationSupport.checkApplicationPermission(WRITE, APPLICATION, deliveryConfig.application)
    deliveryConfig.serviceAccount?.also {
      authorizationSupport.checkServiceAccountAccess(SERVICE_ACCOUNT, it)
    }

    return upsert(deliveryConfig)
  }
}
