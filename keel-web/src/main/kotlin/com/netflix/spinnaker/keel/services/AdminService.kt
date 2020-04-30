package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.KeelRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AdminService(
  private val repository: KeelRepository,
  private val actuationPauser: ActuationPauser
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
}
