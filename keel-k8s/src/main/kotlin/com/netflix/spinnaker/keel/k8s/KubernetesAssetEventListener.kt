package com.netflix.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.events.AssetEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class KubernetesAssetEventListener {

  @EventListener
  fun handle(event: AssetEvent) {
    log.info("Received event {}", event)
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
