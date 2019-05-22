package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ResourceCheckScheduler(
  private val resourceRepository: ResourceRepository,
  private val resourceActuator: ResourceActuator,
  @Value("\${keel.resource-check.min-age-minutes:1}") private val resourceCheckMinAgeMinutes: Long,
  @Value("\${keel.resource-check.batch-size:1}") private val resourceCheckBatchSize: Int
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

  @Scheduled(fixedDelayString = "\${keel.resource-check.frequency:PT1S}")
  fun checkResources() {
    if (enabled) {
      log.debug("Starting scheduled validation…")
      resourceRepository
        .nextResourcesDueForCheck(Duration.ofMinutes(resourceCheckMinAgeMinutes), resourceCheckBatchSize)
        .forEach { (_, name, apiVersion, kind) ->
          resourceActuator.checkResource(name, apiVersion, kind)
        }
      log.debug("Scheduled validation complete")
    } else {
      log.debug("Scheduled validation disabled")
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
