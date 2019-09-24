package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.NoImageFound
import com.netflix.spinnaker.keel.api.NoImageFoundForRegions
import com.netflix.spinnaker.keel.api.NoImageSatisfiesConstraints
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.UnsupportedStrategy
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ClusterRegion
import com.netflix.spinnaker.keel.api.ec2.IdImageProvider
import com.netflix.spinnaker.keel.api.ec2.ImageProvider
import com.netflix.spinnaker.keel.api.ec2.JenkinsImageProvider
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
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
  suspend fun resolveImageId(resource: Resource<ClusterSpec>): ResolvedImages =
    when (val imageProvider = resource.spec.imageProvider) {
      is IdImageProvider -> resolveFromImageId(resource, imageProvider)
      is ArtifactImageProvider -> resolveFromArtifact(resource, imageProvider)
      is JenkinsImageProvider -> resolveFromJenkinsJob(resource, imageProvider)
      else -> throw UnsupportedStrategy(
        imageProvider::class.simpleName.orEmpty(),
        ImageProvider::class.simpleName.orEmpty()
      )
    }

  private suspend fun resolveFromImageId(
    resource: Resource<ClusterSpec>,
    imageProvider: IdImageProvider
  ): ResolvedImages {
    val images = cloudDriverService.namedImages(
      serviceAccount = DEFAULT_SERVICE_ACCOUNT,
      imageName = imageProvider.imageId,
      account = null,
      region = null
    )
    check(images.size == 1) {
      "Expected a single image for AMI id ${imageProvider.imageId} but found ${images.size}"
    }

    // TODO: this is not going to work for multiple regions
    return ResolvedImages(
      images.first().appVersion,
      resource.spec.locations.regions.associate { it.region to imageProvider.imageId }
    )
  }

  private suspend fun resolveFromArtifact(
    resource: Resource<ClusterSpec>,
    imageProvider: ArtifactImageProvider
  ): ResolvedImages {
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
        account = account
      ) ?: throw NoImageFound(artifactVersion)

      ResolvedImages(
        artifactVersion,
        image.toResolvedImage(resource.spec.locations.regions.map(ClusterRegion::region))
      )
    } else {
      val image = imageService.getLatestNamedImage(
        packageName = artifact.name,
        account = account
      ) ?: throw NoImageFound(artifact.name)

      ResolvedImages(
        image.appVersion,
        image.toResolvedImage(resource.spec.locations.regions.map(ClusterRegion::region))
      )
    }
  }

  private suspend fun resolveFromJenkinsJob(
    resource: Resource<ClusterSpec>,
    imageProvider: JenkinsImageProvider
  ): ResolvedImages {
    val image = imageService.getNamedImageFromJenkinsInfo(
      imageProvider.packageName,
      dynamicConfigService.getConfig("images.default-account", "test"),
      imageProvider.buildHost,
      imageProvider.buildName,
      imageProvider.buildNumber
    ) ?: throw NoImageFound(imageProvider.packageName)

    log.info("Image found for {}: {}", imageProvider.packageName, image)
    return ResolvedImages(
      image.appVersion,
      image.toResolvedImage(resource.spec.locations.regions.map(ClusterRegion::region))
    )
  }

  private fun NamedImage.toResolvedImage(regions: Collection<String>) =
    amis
      .filterKeys { it in regions }
      .mapValues { (_, amis) -> amis?.first() }
      .filterNotNullValues()
      .also {
        if (it.size < regions.size) {
          throw NoImageFoundForRegions(imageName, regions - it.keys)
        }
      }

  @Suppress("UNCHECKED_CAST")
  private fun <K, V> Map<out K, V?>.filterNotNullValues(): Map<K, V> =
    filterValues { it != null } as Map<K, V>

  private inline fun <reified T> DynamicConfigService.getConfig(configName: String, defaultValue: T) =
    getConfig(T::class.java, configName, defaultValue)
}

data class ResolvedImages(
  val appVersion: String,
  val imagesByRegion: Map<String, String>
)
