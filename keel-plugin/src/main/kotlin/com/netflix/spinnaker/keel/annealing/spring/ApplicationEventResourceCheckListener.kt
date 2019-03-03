package com.netflix.spinnaker.keel.annealing.spring

import com.netflix.spinnaker.keel.annealing.ResourceActuator
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async

open class ApplicationEventResourceCheckListener(
  private val resourceActuator: ResourceActuator
) {
  @Async
  @EventListener(ResourceCheckEvent::class)
  open fun onResourceCheck(event: ResourceCheckEvent) {
    with(event) {
      log.debug("Received request to check {}", name)
      resourceActuator.checkResource(name, apiVersion, kind)
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
