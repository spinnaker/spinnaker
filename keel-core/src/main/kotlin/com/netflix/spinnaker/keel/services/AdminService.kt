package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.StatefulConstraint
import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.exceptions.NoSuchEnvironmentException
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AdminService(
  private val repository: KeelRepository,
  private val actuationPauser: ActuationPauser,
  private val diffFingerprintRepository: DiffFingerprintRepository
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  fun deleteApplicationData(application: String) {
    log.debug("Deleting all data for application: $application")
    val config = repository.getDeliveryConfigForApplication(application)
    repository.deleteDeliveryConfig(config.name)
  }

  fun getPausedApplications() = actuationPauser.pausedApplications()

  fun getManagedApplications(): Collection<ApplicationSummary> {
    return repository.getApplicationSummaries()
  }

  fun triggerRecheck(resourceId: String) {
    log.info("Triggering a recheck for $resourceId by clearing our record of the diff")
    diffFingerprintRepository.clear(resourceId)
  }

  /**
   * Removes the stored state we have for any stateful constraints for an environment
   * so they will evaluate again
   */
  fun forceConstraintReevaluation(application: String, environment: String, type: String? = null) {
    log.info("[app=$application, env=$environment] Forcing reevaluation of stateful constraints.")
    if (type != null) {
      log.info("[app=$application, env=$environment] Forcing only type $type")
    }

    val deliveryConfig = repository.getDeliveryConfigForApplication(application)
    val env = deliveryConfig.environments.find { it.name == environment }
      ?: throw NoSuchEnvironmentException(environment, application)
    env.constraints.forEach { constraint ->
      if (constraint is StatefulConstraint) {
        if (type == null || type == constraint.type) {
          log.info("[app=$application, env=$environment] Deleting constraint state for ${constraint.type}.")
          repository.deleteConstraintState(deliveryConfig.name, environment, constraint.type)
        }
      }
    }
  }
}
