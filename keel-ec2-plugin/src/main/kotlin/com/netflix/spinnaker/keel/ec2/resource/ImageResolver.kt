package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.NoImageFound
import com.netflix.spinnaker.keel.api.NoImageFoundForRegion
import com.netflix.spinnaker.keel.api.NoImageSatisfiesConstraints
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.UnsupportedStrategy
import com.netflix.spinnaker.keel.api.ec2.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.image.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.IdImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.ImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.JenkinsImageProvider
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.slf4j.LoggerFactory

class ImageResolver(
  private val dynamicConfigService: DynamicConfigService,
  private val cloudDriverService: CloudDriverService,
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val artifactRepository: ArtifactRepository,
  private val imageService: ImageService
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  // TODO: we may (probably will) want to make the parameter type more generic
  suspend fun resolveImageId(resource: Resource<ServerGroupSpec>): Pair<String, String> =
    when (val imageProvider = resource.spec.launchConfiguration.imageProvider) {
      is IdImageProvider -> {
        resolveFromImageId(imageProvider)
      }
      is ArtifactImageProvider -> {
        resolveFromArtifact(resource, imageProvider)
      }
      is JenkinsImageProvider -> {
        resolveFromJenkinsJob(resource, imageProvider)
      }
      else -> {
        throw UnsupportedStrategy(imageProvider::class.simpleName.orEmpty(), ImageProvider::class.simpleName.orEmpty())
      }
    }

  private suspend fun resolveFromImageId(imageProvider: IdImageProvider): Pair<String, String> {
    val images = cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, imageProvider.imageId, null, null)
    check(images.size == 1) {
      "Expected a single image for AMI id ${imageProvider.imageId} but found ${images.size}"
    }
    return imageProvider.imageId to images.first().appVersion
  }

  private suspend fun resolveFromArtifact(resource: Resource<ServerGroupSpec>, imageProvider: ArtifactImageProvider): Pair<String, String> {
    val region = resource.spec.location.region
    val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(resource.id)
    val environment = deliveryConfigRepository.environmentFor(resource.id)
    val artifact = imageProvider.deliveryArtifact
    val account = dynamicConfigService.getConfig("images.default-account", "test")

    return if (deliveryConfig != null && environment != null) {
      val artifactVersion = artifactRepository.latestVersionApprovedIn(
        deliveryConfig,
        artifact,
        environment.name,
        imageProvider.artifactStatuses
      ) ?: throw NoImageSatisfiesConstraints(artifact.name, environment.name)
      val image = imageService.getLatestNamedImage(
        appVersion = AppVersion.parseName(artifactVersion),
        account = account,
        region = region
      ) ?: throw NoImageFound(artifactVersion)

      val imageId = image.amis[region]?.first()
        ?.also {
          log.info("Image found for {}: {}", artifactVersion, image)
        } ?: throw NoImageFoundForRegion(artifactVersion, region)
      imageId to artifactVersion
    } else {
      val image = imageService.getLatestNamedImage(
        packageName = artifact.name,
        account = account,
        region = region
      ) ?: throw NoImageFound(artifact.name)

      val imageId = (image.amis[region]?.first()
        ?.also {
          log.info("Image found for {}: {}", artifact.name, image)
        }
        ?: throw NoImageFoundForRegion(artifact.name, region))
      imageId to image.appVersion
    }
  }

  private suspend fun resolveFromJenkinsJob(resource: Resource<ServerGroupSpec>, imageProvider: JenkinsImageProvider): Pair<String, String> {
    val region = resource.spec.location.region
    val namedImage = imageService.getNamedImageFromJenkinsInfo(
      imageProvider.packageName,
      dynamicConfigService.getConfig("images.default-account", "test"),
      imageProvider.buildHost,
      imageProvider.buildName,
      imageProvider.buildNumber
    ) ?: throw NoImageFound(imageProvider.packageName)

    log.info("Image found for {}: {}", imageProvider.packageName, namedImage)
    val amis = namedImage.amis[region]
      ?: throw NoImageFoundForRegion(imageProvider.packageName, region)
    return amis.first() to namedImage.appVersion // todo eb: when are there multiple?
  }

  private inline fun <reified T> DynamicConfigService.getConfig(configName: String, defaultValue: T) =
    getConfig(T::class.java, configName, defaultValue)
}
