package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.NoImageFound
import com.netflix.spinnaker.keel.api.NoImageFoundForRegion
import com.netflix.spinnaker.keel.api.NoImageSatisfiesConstraints
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.UnsupportedStrategy
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.image.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.IdImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.ImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.JenkinsImageProvider
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImageComparator
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.slf4j.LoggerFactory

class ImageResolver(
  private val dynamicConfigService: DynamicConfigService,
  private val cloudDriverService: CloudDriverService,
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val promotionRepository: ArtifactRepository,
  private val imageService: ImageService
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  // TODO: we may (probably will) want to make the parameter type more generic
  suspend fun resolveImageId(resource: Resource<ClusterSpec>): String {
    when (val imageProvider = resource.spec.launchConfiguration.imageProvider) {
      is IdImageProvider -> {
        return imageProvider.imageId
      }
      is ArtifactImageProvider -> {
        val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(resource.uid)
        val environment = deliveryConfigRepository.environmentFor(resource.uid)
        val artifact = imageProvider.deliveryArtifact
        val artifactName = if (deliveryConfig != null && environment != null) {
          val version = promotionRepository.latestVersionApprovedIn(
            deliveryConfig,
            artifact,
            environment.name
          ) ?: throw NoImageSatisfiesConstraints(artifact.name, environment.name)
          "${artifact.name}-$version"
        } else {
          artifact.name
        }
        val account = dynamicConfigService.getConfig("images.default-account", "test")
        val namedImage = cloudDriverService
          .namedImages(resource.serviceAccount, artifactName, account)
          .sortedWith(NamedImageComparator)
          .lastOrNull() ?: throw NoImageFound(artifactName)

        log.info("Image found for {}: {}", artifactName, namedImage)

        return namedImage.amis[resource.spec.location.region]?.first()
          ?: throw NoImageFoundForRegion(artifactName, resource.spec.location.region) // todo eb: when are there multiple?
      }
      is JenkinsImageProvider -> {
        val namedImage = imageService.getNamedImageFromJenkinsInfo(
          imageProvider.packageName,
          dynamicConfigService.getConfig("images.default-account", "test"),
          imageProvider.buildHost,
          imageProvider.buildName,
          imageProvider.buildNumber
        ) ?: throw NoImageFound(imageProvider.packageName)

        log.info("Image found for {}: {}", imageProvider.packageName, namedImage)
        val amis = namedImage.amis[resource.spec.location.region]
          ?: throw NoImageFoundForRegion(imageProvider.packageName, resource.spec.location.region)
        return amis.first() // todo eb: when are there multiple?
      }
      else -> {
        throw UnsupportedStrategy(imageProvider::class.simpleName.orEmpty(), ImageProvider::class.simpleName.orEmpty())
      }
    }
  }

  private inline fun <reified T> DynamicConfigService.getConfig(configName: String, defaultValue: T) =
    getConfig(T::class.java, configName, defaultValue)
}
