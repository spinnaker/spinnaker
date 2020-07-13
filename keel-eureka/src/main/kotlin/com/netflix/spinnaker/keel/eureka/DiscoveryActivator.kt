package com.netflix.spinnaker.keel.eureka

import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener

class DiscoveryActivator(
  private val publisher: ApplicationEventPublisher
) : ApplicationListener<RemoteStatusChangedEvent> {

  override fun onApplicationEvent(event: RemoteStatusChangedEvent) =
    event.source.let { e ->
      if (e.status == UP && e.previousStatus != UP) {
        log.info("Instance is ${e.status}... ${javaClass.simpleName} starting")
        publisher.publishEvent(ApplicationUp)
      } else if (e.previousStatus == UP) {
        log.info("Instance is ${e.status}... ${javaClass.simpleName} stopping")
        publisher.publishEvent(ApplicationDown)
      }
    }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
