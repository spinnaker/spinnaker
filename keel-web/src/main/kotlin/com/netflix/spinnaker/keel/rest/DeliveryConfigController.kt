package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.auth.AuthorizationSupport.TargetEntity.APPLICATION
import com.netflix.spinnaker.keel.auth.AuthorizationSupport.TargetEntity.SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.auth.PermissionLevel.READ
import com.netflix.spinnaker.keel.auth.PermissionLevel.WRITE
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.parseDeliveryConfig
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoDeliveryConfigForApplication
import com.netflix.spinnaker.keel.schema.Generator
import com.netflix.spinnaker.keel.schema.RootSchema
import com.netflix.spinnaker.keel.schema.generateSchema
import com.netflix.spinnaker.keel.upsert.DeliveryConfigUpserter
import com.netflix.spinnaker.keel.validators.DeliveryConfigProcessor
import com.netflix.spinnaker.keel.validators.DeliveryConfigValidator
import com.netflix.spinnaker.keel.validators.applyAll
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import io.swagger.v3.oas.annotations.Operation
import kotlinx.coroutines.runBlocking
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
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.io.BufferedReader
import java.io.InputStream


@RestController
@RequestMapping(path = ["/delivery-configs"])
class DeliveryConfigController(
  private val repository: KeelRepository,
  private val validator: DeliveryConfigValidator,
  private val importer: DeliveryConfigImporter,
  private val authorizationSupport: AuthorizationSupport,
  @Autowired(required = false) private val deliveryConfigProcessors: List<DeliveryConfigProcessor> = emptyList(),
  private val generator: Generator,
  private val deliveryConfigUpserter: DeliveryConfigUpserter,
  private val yamlMapper: YAMLMapper,
  private val front50Cache: Front50Cache,
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  data class GateRawConfig(
    val content: String
  )

  @Operation(
    description = "Registers or updates a delivery config manifest."
  )
  @PostMapping(
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun upsert(
    stream: InputStream,
    @RequestHeader("X-SPINNAKER-USER") user: String
  ): DeliveryConfig {
    val rawDeliveryConfig = readRawConfigFromStream(stream)
    return upsertConfigIfAuthorized(rawDeliveryConfig, user)
  }

  @Operation(
    description = "Registers or updates a delivery config manifest from Gate"
  )
  @PostMapping(
    path = ["/upsertGate"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  // We had to handle requests from gate separately, because gate was serializing the raw string incorrectly. Therefore, it's wrapped in a simple object
  fun upsertFromGate(@RequestBody rawConfig: GateRawConfig,  @RequestHeader("X-SPINNAKER-USER") user: String): DeliveryConfig {
    return upsertConfigIfAuthorized(rawConfig.content, user)
  }

  private fun upsertConfigIfAuthorized(rawDeliveryConfig: String, user: String): DeliveryConfig {
    val submittedDeliveryConfig = yamlMapper.parseDeliveryConfig(rawDeliveryConfig)
    submittedDeliveryConfig.checkPermissions()
    deliveryConfigProcessors.applyAll(submittedDeliveryConfig).let {
      log.debug("Upserting config of app ${submittedDeliveryConfig.application}")
      val (deliveryConfig, isNew) = deliveryConfigUpserter.upsertConfig(it)
      if (isNew) {
        // We need to update front50 to enable the git integration to import future delivery config changes
        runBlocking {
          front50Cache.updateManagedDeliveryConfig(submittedDeliveryConfig.application, user, ManagedDeliveryConfig(importDeliveryConfig = true))
        }
      }
      return deliveryConfig
    }
  }


  @GetMapping(
    path = ["/{name}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('READ', 'DELIVERY_CONFIG', #name)
    and @authorizationSupport.hasCloudAccountPermission('READ', 'DELIVERY_CONFIG', #name)"""
  )
  fun get(@PathVariable("name") name: String): DeliveryConfig =
    repository.getDeliveryConfig(name)

  @DeleteMapping(
    path = ["/{name}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('WRITE', 'DELIVERY_CONFIG', #name)
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
  fun diff(@RequestBody deliveryConfig: SubmittedDeliveryConfig): Map<String, Any?> {
    val existing: DeliveryConfig? = try {
      repository.getDeliveryConfigForApplication(deliveryConfig.application)
    } catch (e: NoDeliveryConfigForApplication) {
      null
    }

    val diff = deliveryConfigProcessors
      .applyAll(deliveryConfig)
      .let { processedDeliveryConfig ->
        validator.validate(processedDeliveryConfig)
        DefaultResourceDiff(
          desired = processedDeliveryConfig.toDeliveryConfig(),
          current = existing
        ).toDeltaJson()
      }

    return diff
  }

  @PostMapping(
    path = ["/validate"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  fun validate(stream: InputStream) {
    val rawDeliveryConfig = readRawConfigFromStream(stream)
    validateRawConfig(rawDeliveryConfig)
  }

  @Operation(
    description = "Validate the provided delivery config"
  )
  @PostMapping(
    path = ["validate/gate"],
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  // We had to handle requests from gate separately, because gate was serializing the raw string incorrectly. Therefore, it's wrapped in a simple object
  fun validateFromGate(@RequestBody rawConfig: GateRawConfig) {
    validateRawConfig(rawConfig.content)
  }

  private fun validateRawConfig(rawDeliveryConfig: String) {
    val config = yamlMapper.parseDeliveryConfig(rawDeliveryConfig)
    authorizationSupport.checkApplicationPermission(READ, APPLICATION, config.application)
    validator.validate(config)
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
    deliveryConfig.checkPermissions()
    return deliveryConfigUpserter.upsertConfig(deliveryConfig).first
  }

  private fun SubmittedDeliveryConfig.checkPermissions() {
    authorizationSupport.checkApplicationPermission(WRITE, APPLICATION, application)
    serviceAccount?.also {
      authorizationSupport.checkServiceAccountAccess(SERVICE_ACCOUNT, it)
    }
  }

  private fun readRawConfigFromStream(stream: InputStream): String {
    var rawDeliveryConfig: String
    BufferedReader(stream.reader()).use { reader ->
      rawDeliveryConfig = reader.readText()
    }
    return rawDeliveryConfig
  }


  @Operation(
    description = "Responds with the JSON schema for POSTing to /delivery-configs"
  )
  @GetMapping(
    path = ["/schema"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun schema(): RootSchema = generator.generateSchema<SubmittedDeliveryConfig>()
}
