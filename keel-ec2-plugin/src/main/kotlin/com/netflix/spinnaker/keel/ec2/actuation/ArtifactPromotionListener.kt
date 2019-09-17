package com.netflix.spinnaker.keel.ec2.actuation

import com.netflix.spinnaker.keel.api.ec2.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.image.ArtifactImageProvider
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(CloudDriverService::class)
class ArtifactPromotionListener(
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val artifactRepository: ArtifactRepository
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @EventListener(ResourceDeltaResolved::class)
  fun onDeltaResolved(event: ResourceDeltaResolved) {
    val desired = event.desired
    if (desired is ServerGroupSpec && desired.launchConfiguration.imageProvider is ArtifactImageProvider) {
      val current = event.current as? ServerGroup
      checkNotNull(current) {
        "Current resource state is a ${event.current.javaClass.simpleName} when a ${ServerGroup::class.java.simpleName} was expected"
      }
      val appVersion = current.launchConfiguration.appVersion
      val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(event.resourceId)
      val environment = deliveryConfigRepository.environmentFor(event.resourceId)
      if (deliveryConfig == null || environment == null) {
        log.warn("Resource ${event.resourceId} is not part of a delivery environment")
      } else {
        log.info("Marking {} as successfully deployed to {}'s {} environment", appVersion, deliveryConfig.name, environment.name)
        artifactRepository.markAsSuccessfullyDeployedTo(
          deliveryConfig,
          desired.launchConfiguration.imageProvider.deliveryArtifact,
          appVersion,
          environment.name
        )
      }
    }
  }
}
