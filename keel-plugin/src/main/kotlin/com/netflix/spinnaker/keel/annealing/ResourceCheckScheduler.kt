package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.sync.Lock
import com.netflix.spinnaker.keel.telemetry.LockAttemptFailed
import com.netflix.spinnaker.keel.telemetry.LockAttemptSucceeded
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ResourceCheckScheduler(
  private val resourceRepository: ResourceRepository,
  private val resourceCheckQueue: ResourceCheckQueue,
  private val lock: Lock,
  private val publisher: ApplicationEventPublisher,
  @Value("\${keel.resource.monitoring.frequency.ms:60000}")
  private val frequencyMs: Long
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
    when {
      !enabled ->
        log.debug("Scheduled validation disabled")
      !lock.tryAcquire(LOCK_NAME, Duration.ofMillis(frequencyMs)) -> {
        log.debug("Failed to acquire lock - another instance is checking resources")
        publisher.publishEvent(LockAttemptFailed)
      }
      else -> {
        log.debug("Starting scheduled validation…")
        publisher.publishEvent(LockAttemptSucceeded)
        resourceRepository.allResources { (_, name, _, apiVersion, kind) ->
          resourceCheckQueue.scheduleCheck(name, apiVersion, kind)
        }
        log.debug("Scheduled validation complete")
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    const val LOCK_NAME = "resource-check"
  }
}
