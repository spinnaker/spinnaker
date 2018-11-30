package com.netflix.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.events.AssetEvent
import com.netflix.spinnaker.keel.events.AssetEventType
import io.kubernetes.client.ApiException
import io.kubernetes.client.apis.CustomObjectsApi
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class KubernetesAssetEventListener(
  private val customObjectsApi: CustomObjectsApi
) {

  @EventListener
  fun handle(event: AssetEvent) {
    log.info("Received event {}", event)
    try {
      when (event.type) {
        AssetEventType.CREATE -> customObjectsApi
          .createClusterCustomObject(
            event.asset.apiVersion.group,
            event.asset.apiVersion.version,
            event.asset.kind.substringBefore(".") + "s",
            event.asset,
            "true"
          )
        else -> TODO()
      }
    } catch (e: ApiException) {
      log.error("Exception sending event to Kubernetes: {} {}", e.code, e.responseBody)
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
