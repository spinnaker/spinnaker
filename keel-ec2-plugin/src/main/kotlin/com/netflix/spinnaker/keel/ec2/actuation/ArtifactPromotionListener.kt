package com.netflix.spinnaker.keel.ec2.actuation

import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.Cluster
import com.netflix.spinnaker.keel.api.ec2.image.ArtifactImageProvider
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(CloudDriverService::class)
class ArtifactPromotionListener(
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val artifactRepository: ArtifactRepository,
  private val cloudDriverService: CloudDriverService
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @EventListener(ResourceDeltaResolved::class)
  fun onDeltaResolved(event: ResourceDeltaResolved) {
    val desired = event.desired
    if (desired is ClusterSpec && desired.launchConfiguration.imageProvider is ArtifactImageProvider) {
      val current = event.current as? Cluster
      checkNotNull(current) {
        "Current resource state is a ${event.current.javaClass.simpleName} when a ${Cluster::class.java.simpleName} was expected"
      }
      val deployedImage = current.launchConfiguration.imageId
      val amis = runBlocking {
        cloudDriverService.namedImages(deployedImage, desired.location.accountName, desired.location.region)
      }
      check(amis.size == 1) {
        "Found ${amis.size} images for image id $deployedImage when 1 was expected"
      }
      val appVersion = amis.first().appVersion
      val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(event.uid)
      val environment = deliveryConfigRepository.environmentFor(event.uid)
      if (deliveryConfig == null || environment == null) {
        log.warn("Resource ${event.id} is not part of a delivery environment")
      } else {
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
