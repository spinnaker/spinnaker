package com.netflix.spinnaker.keel.upsert

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.events.DeliveryConfigChangedNotification
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoDeliveryConfigForApplication
import com.netflix.spinnaker.keel.persistence.PersistenceRetry
import com.netflix.spinnaker.keel.persistence.RetryCategory
import com.netflix.spinnaker.keel.validators.DeliveryConfigValidator
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.core.env.Environment as SpringEnvironment

/**
 * The [DeliveryConfigUpserter] is responsible for handling a new [SubmittedDeliveryConfig],
 * including validation, insertion to the DB and notifying the user if necessary
 */
@Component
class DeliveryConfigUpserter(
  private val repository: KeelRepository,
  private val mapper: ObjectMapper,
  private val validator: DeliveryConfigValidator,
  private val publisher: ApplicationEventPublisher,
  private val springEnv: SpringEnvironment,
  private val persistenceRetry: PersistenceRetry
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val sendConfigChangedNotification: Boolean
    get() = springEnv.getProperty("keel.notifications.send-config-changed", Boolean::class.java, true)

  /**
   * This function returns the upsertted [DeliveryConfig] and a boolean indicating if the config was just inserted for the first time
   */
  fun upsertConfig(deliveryConfig: SubmittedDeliveryConfig, gitMetadata: GitMetadata? = null): Pair<DeliveryConfig, Boolean>  {
    val existing: DeliveryConfig? = try {
      repository.getDeliveryConfigForApplication(deliveryConfig.application)
    } catch (e: NoDeliveryConfigForApplication) {
      null
    }
    log.info("Validating config for app ${deliveryConfig.application}")
    validator.validate(deliveryConfig)
    log.debug("Upserting delivery config '${deliveryConfig.name}' for app '${deliveryConfig.application}'")
    val config = persistenceRetry.withRetry(RetryCategory.WRITE) {
        repository.upsertDeliveryConfig(deliveryConfig)
      }
    if (shouldNotifyOfConfigChange(existing, config)) {
      log.debug("Publish deliveryConfigChange event for app ${deliveryConfig.application}")
      publisher.publishEvent(
        DeliveryConfigChangedNotification(
          config = config,
          gitMetadata = gitMetadata ?: getGitMetadata(deliveryConfig),
          new = existing == null
        )
      )
    } else {
      log.debug("No config changes for app ${deliveryConfig.application}. Skipping notification")
    }
    return Pair(config.copy(rawConfig = null), existing == null)
  }

  private fun getGitMetadata(deliveryConfig: SubmittedDeliveryConfig): GitMetadata? {
    val metadata: Map<String, Any?> = deliveryConfig.metadata ?: mapOf()
    return try {
      val candidateMetadata = metadata.getOrDefault("gitMetadata", null)
      if (candidateMetadata != null) {
        mapper.convertValue<GitMetadata>(candidateMetadata)
      } else {
        null
      }
    } catch (e: IllegalArgumentException) {
      log.debug("Error converting git metadata ${metadata.getOrDefault("gitMetadata", null)}: {}", e)
      // not properly formed, so ignore the metadata and move on
      null
    }
  }

  fun shouldNotifyOfConfigChange(existing: DeliveryConfig?, new: DeliveryConfig) =
    when {
      !sendConfigChangedNotification -> false
      existing == null -> true
      DefaultResourceDiff(existing, new).also {
        if (it.hasChanges()) {
          log.debug("Found diffs in delivery config ${it.affectedRootPropertyNames}")
        }
      }.hasChanges() -> true
      else -> false
    }
}
