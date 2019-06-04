package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.coroutines.CoroutineContext

@Component
class ResourceCheckScheduler(
  private val resourceRepository: ResourceRepository,
  private val resourceActuator: ResourceActuator,
  @Value("\${keel.resource-check.min-age-minutes:1}") private val resourceCheckMinAgeMinutes: Long,
  @Value("\${keel.resource-check.batch-size:1}") private val resourceCheckBatchSize: Int,
  private val publisher: ApplicationEventPublisher
) : CoroutineScope {
  override val coroutineContext: CoroutineContext = Dispatchers.IO

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
      publisher.publishEvent(ScheduledCheckStarting)

      val job = launch {
        resourceRepository
          .nextResourcesDueForCheck(Duration.ofMinutes(resourceCheckMinAgeMinutes), resourceCheckBatchSize)
          .forEach { (_, name, apiVersion, kind) ->
            launch {
              resourceActuator.checkResource(name, apiVersion, kind)
            }
          }
      }

      runBlocking { job.join() }
      log.debug("Scheduled validation complete")
    } else {
      log.debug("Scheduled validation disabled")
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
