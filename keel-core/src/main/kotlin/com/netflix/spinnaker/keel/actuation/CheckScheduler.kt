package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.persistence.AgentLockRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
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

@Component
class CheckScheduler(
  private val repository: KeelRepository,
  private val resourceActuator: ResourceActuator,
  private val environmentPromotionChecker: EnvironmentPromotionChecker,
  @Value("\${keel.resource-check.min-age-duration:60s}") private val resourceCheckMinAgeDuration: Duration,
  @Value("\${keel.resource-check.batch-size:1}") private val resourceCheckBatchSize: Int,
  private val publisher: ApplicationEventPublisher,
  private val agentLockRepository: AgentLockRepository
) : CoroutineScope {
  override val coroutineContext: CoroutineContext = Dispatchers.IO

  private val enabled = AtomicBoolean(false)

  @EventListener(ApplicationUp::class)
  fun onApplicationUp() {
    log.info("Application up, enabling scheduled resource checks")
    enabled.set(true)
  }

  @EventListener(ApplicationDown::class)
  fun onApplicationDown() {
    log.info("Application down, disabling scheduled resource checks")
    enabled.set(false)
  }

  @Scheduled(fixedDelayString = "\${keel.resource-check.frequency:PT1S}")
  fun checkResources() {
    if (enabled.get()) {
      log.debug("Starting scheduled resource validation…")
      publisher.publishEvent(ScheduledResourceCheckStarting)

      val job = launch {
        repository
          .resourcesDueForCheck(resourceCheckMinAgeDuration, resourceCheckBatchSize)
          .forEach {
            launch { resourceActuator.checkResource(it) }
          }
      }

      runBlocking { job.join() }
      log.debug("Scheduled resource validation complete")
    } else {
      log.debug("Scheduled resource validation disabled")
    }
  }

  @Scheduled(fixedDelayString = "\${keel.environment-check.frequency:PT1S}")
  fun checkEnvironments() {
    if (enabled.get()) {
      log.debug("Starting scheduled environment validation…")
      publisher.publishEvent(ScheduledEnvironmentCheckStarting)

      val job = launch {
        repository
          .deliveryConfigsDueForCheck(resourceCheckMinAgeDuration, resourceCheckBatchSize)
          .forEach {
            launch { environmentPromotionChecker.checkEnvironments(it) }
          }
      }

      runBlocking { job.join() }
      log.debug("Scheduled environment validation complete")
    } else {
      log.debug("Scheduled environment validation disabled")
    }
  }

  @Scheduled(fixedDelayString = "\${keel.scheduled.agent.frequency:PT1M}")
  fun invokeAgent() {
    if (enabled.get()) {
      agentLockRepository.agents.forEach {
        val agentName: String = it.javaClass.simpleName
        val lockAcquired = agentLockRepository.tryAcquireLock(agentName, it.lockTimeoutSeconds)
        if (lockAcquired) {
          runBlocking {
            log.debug("invoking $agentName")
            it.invokeAgent()
          }
          log.debug("invoking $agentName completed")
        }
      }
    } else {
      log.debug("invoking agent disabled")
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
