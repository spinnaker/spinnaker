package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.PeriodicallyCheckedRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import java.time.Duration
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
  private val resourceRepository: ResourceRepository,
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val resourceActuator: ResourceActuator,
  private val environmentPromotionChecker: EnvironmentPromotionChecker,
  @Value("\${keel.resource-check.min-age-duration:60s}") private val resourceCheckMinAgeDuration: Duration,
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
      log.debug("Starting scheduled resource validation…")
      publisher.publishEvent(ScheduledResourceCheckStarting)

      val job = launch {
        resourceRepository
          .launchForEachItem {
            resourceActuator.checkResource(it)
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
    if (enabled) {
      log.debug("Starting scheduled environment validation…")
      publisher.publishEvent(ScheduledEnvironmentCheckStarting)

      val job = launch {
        deliveryConfigRepository
          .launchForEachItem {
            environmentPromotionChecker.checkEnvironments(it)
          }
      }

      runBlocking { job.join() }
      log.debug("Scheduled environment validation complete")
    } else {
      log.debug("Scheduled environment validation disabled")
    }
  }

  private fun <T : Any> PeriodicallyCheckedRepository<T>.launchForEachItem(block: suspend CoroutineScope.(T) -> Unit) {
    itemsDueForCheck(resourceCheckMinAgeDuration, resourceCheckBatchSize)
      .forEach {
        launch { block(it) }
      }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
