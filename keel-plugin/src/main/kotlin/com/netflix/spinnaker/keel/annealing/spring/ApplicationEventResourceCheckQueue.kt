package com.netflix.spinnaker.keel.annealing.spring

import com.netflix.spinnaker.keel.annealing.ResourceCheckQueue
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ResourceName
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

/**
 * Simple command queue implementation backed by Spring application event bus.
 *
 * This is fine locally but restricts handling of commands to the same Keel instance that fired
 * them which is not what we want in production.
 */
class ApplicationEventResourceCheckQueue(
  private val publisher: ApplicationEventPublisher
) : ResourceCheckQueue {

  override fun scheduleCheck(name: ResourceName, apiVersion: ApiVersion, kind: String) {
    log.debug("Requesting check of {}", name)
    publisher.publishEvent(ResourceCheckEvent(name, apiVersion, kind))
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
