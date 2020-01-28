package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.pause.ResourcePauser
import com.netflix.spinnaker.keel.persistence.Cleaner
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import org.slf4j.LoggerFactory.getLogger
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/admin"])
class AdminController(
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val resourcePauser: ResourcePauser,
  private val cleaner: Cleaner
) {
  private val log by lazy { getLogger(javaClass) }

  @DeleteMapping(
    path = ["/applications/{application}"]
  )
  @ResponseStatus(NO_CONTENT)
  fun deleteApplicationData(
    @PathVariable("application") application: String
  ) {
    log.debug("Deleting all data for application: $application")
    deliveryConfigRepository.getByApplication(application).forEach { config ->
      cleaner.delete(config.name)
    }
  }

  @GetMapping(
    path = ["/applications/paused"]
  )
  fun getPausedApplications() =
    resourcePauser.pausedApplications()
}
