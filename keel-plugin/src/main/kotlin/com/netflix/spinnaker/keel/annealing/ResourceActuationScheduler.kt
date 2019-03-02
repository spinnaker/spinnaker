package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.persistence.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ResourceActuationScheduler(
  private val resourceRepository: ResourceRepository,
  private val publisher: ApplicationEventPublisher
) {

  @Scheduled(fixedDelayString = "\${keel.resource.monitoring.frequency.ms:60000}")
  fun validateManagedResources() {
    log.debug("Starting scheduled validation…")
    resourceRepository.allResources { (name, apiVersion, kind) ->
      log.debug("Requesting validation of {}", name)
      publisher.publishEvent(ResourceCheckEvent(name, apiVersion, kind))
    }
    log.debug("Scheduled validation complete")
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
