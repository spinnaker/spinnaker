package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ResourceCheckScheduler(
  private val resourceRepository: ResourceRepository,
  private val resourceCheckQueue: ResourceCheckQueue
) {

  private var enabled = false

  @EventListener(ApplicationUp::class)
  fun onApplicationUp() {
    log.info("Application up, enabling scheduled resource checks")
    enabled = true
  }

  @EventListener(ApplicationDown::class)
  fun onApplicationDown() {
    log.info("Application down, disabling scheduled resource checks")
    enabled = false
  }

  @Scheduled(fixedDelayString = "\${keel.resource.monitoring.frequency.ms:60000}")
  fun checkManagedResources() {
    if (enabled) {
      log.debug("Starting scheduled validation…")
      resourceRepository.allResources { (_, name, _, apiVersion, kind) ->
        resourceCheckQueue.scheduleCheck(name, apiVersion, kind)
      }
      log.debug("Scheduled validation complete")
    } else {
      log.debug("Scheduled validation disabled")
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
