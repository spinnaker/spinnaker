package com.netflix.spinnaker.keel.lifecycle

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.SUCCEEDED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BUILD
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigException
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class EnvironmentVersionForArtifactVersionTrigger(
  private val deliveryConfigRepository: DeliveryConfigRepository,
) {
  @EventListener(LifecycleEvent::class)
  fun onLifecycleEvent(event: LifecycleEvent) {
    if (event.type != BUILD || event.status != SUCCEEDED) {
      return
    }

    val (deliveryConfig, artifact) = event.deliveryConfigAndArtifactOrNull() ?: return

    deliveryConfig.environments.forEach { environment ->
      if (artifact.isUsedIn(environment)) {
        deliveryConfigRepository.addArtifactVersionToEnvironment(
          deliveryConfig,
          environment.name,
          artifact,
          event.artifactVersion
        )
      }
    }
  }

  private fun LifecycleEvent.deliveryConfigAndArtifactOrNull(): Pair<DeliveryConfig, DeliveryArtifact>? {
    return try {
      deliveryConfigRepository.get(deliveryConfigName)
    } catch (e: NoSuchDeliveryConfigException) {
      log.error(e.message)
      null
    }
      ?.let { deliveryConfig ->
        val artifact = deliveryConfig.artifacts.find { it.reference == artifactReference }
        if (artifact == null) {
          log.error("No artifact with reference {} found in {}", artifactReference, deliveryConfigName)
          null
        } else {
          deliveryConfig to artifact
        }
      }
  }


  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
