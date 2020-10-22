package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1
import com.netflix.spinnaker.keel.api.ec2.JenkinsImageProvider
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ReferenceArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.VirtualMachineImage
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.getLatestNamedImages
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.clouddriver.model.baseImageVersion
import com.netflix.spinnaker.keel.ec2.NoImageFound
import com.netflix.spinnaker.keel.ec2.NoImageFoundForRegions
import com.netflix.spinnaker.keel.ec2.NoImageSatisfiesConstraints
import com.netflix.spinnaker.keel.filterNotNullValues
import com.netflix.spinnaker.keel.getConfig
import com.netflix.spinnaker.keel.parseAppVersion
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoMatchingArtifactException
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ImageResolver(
  private val dynamicConfigService: DynamicConfigService,
  private val repository: KeelRepository,
  private val imageService: ImageService
) : Resolver<ClusterSpec> {

  override val supportedKind = EC2_CLUSTER_V1

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  data class VersionedNamedImage(
    val namedImages: Map<String, NamedImage>,
    val artifact: DeliveryArtifact?, // TODO: make this non-nullable
    val version: String
  )

  override fun invoke(resource: Resource<ClusterSpec>): Resource<ClusterSpec> {
    val imageProvider = resource.spec.imageProvider ?: return resource
    val image = runBlocking {
      when (imageProvider) {
        is ReferenceArtifactImageProvider -> resolveFromReference(resource, imageProvider)
        // todo eb: artifact provider is here for backwards compatibility. Remove?
        is ArtifactImageProvider -> resolveFromArtifact(resource, imageProvider.deliveryArtifact as DebianArtifact)
        is JenkinsImageProvider -> resolveFromJenkinsJob(imageProvider)
      }
    }
    return resource.withVirtualMachineImages(image)
  }

  val defaultImageAccount: String
    get() = dynamicConfigService.getConfig("images.default-account", "test")

  private suspend fun resolveFromReference(
    resource: Resource<ClusterSpec>,
    imageProvider: ReferenceArtifactImageProvider
  ): VersionedNamedImage {
    val deliveryConfig = repository.deliveryConfigFor(resource.id)
    val artifact = deliveryConfig.artifacts.find { it.reference == imageProvider.reference && it.type == DEBIAN }
      ?: throw NoMatchingArtifactException(deliveryConfig.name, DEBIAN, imageProvider.reference)

    return resolveFromArtifact(resource, artifact as DebianArtifact)
  }

  private suspend fun resolveFromArtifact(
    resource: Resource<ClusterSpec>,
    artifact: DebianArtifact
  ): VersionedNamedImage {
    val deliveryConfig = repository.deliveryConfigFor(resource.id)
    val environment = repository.environmentFor(resource.id)
    val account = defaultImageAccount
    val regions = resource.spec.locations.regions.map { it.name }

    val artifactVersion = repository.latestVersionApprovedIn(
      deliveryConfig,
      artifact,
      environment.name
    ) ?: throw NoImageSatisfiesConstraints(artifact.name, environment.name)

    val images = imageService.getLatestNamedImages(
      appVersion = artifactVersion.parseAppVersion(),
      account = account,
      regions = regions
    )

    return VersionedNamedImage(images, artifact, artifactVersion)
  }

  private suspend fun resolveFromJenkinsJob(
    imageProvider: JenkinsImageProvider
  ): VersionedNamedImage {
    val image = imageService.getNamedImageFromJenkinsInfo(
      imageProvider.packageName,
      dynamicConfigService.getConfig("images.default-account", "test"),
      imageProvider.buildHost,
      imageProvider.buildName,
      imageProvider.buildNumber
    ) ?: throw NoImageFound(imageProvider.packageName)

    log.info("Image found for {}: {}", imageProvider.packageName, image)
    return VersionedNamedImage(
      namedImages = image.amis.keys.associateWith { image },
      artifact = null,
      version = image.appVersion
    )
  }

  private fun Resource<ClusterSpec>.withVirtualMachineImages(image: VersionedNamedImage): Resource<ClusterSpec> {
    val requiredRegions = spec.locations.regions.map { it.name }

    val missingRegions = requiredRegions - image.namedImages.keys
    if (missingRegions.isNotEmpty()) {
      throw NoImageFoundForRegions(image.version, missingRegions)
    }

    val imageIdByRegion = image
      .namedImages
      .mapValues { (region, image) -> image.amis[region]?.first() }
      .filterNotNullValues()

    val overrides = mutableMapOf<String, ServerGroupSpec>()
    overrides.putAll(spec.overrides)

    requiredRegions.forEach { region ->
      val amiId = imageIdByRegion.getValue(region)
      val namedImage = image.namedImages.getValue(region)
      overrides[region] = overrides[region]
        .withVirtualMachineImage(
          VirtualMachineImage(
            amiId,
            namedImage.appVersion,
            namedImage.baseImageVersion
          )
        )
    }

    return copy(
      spec = spec.copy(
        overrides = overrides,
        _artifactName = image.artifact?.name
          ?: error("Artifact not found in images ${image.namedImages.values.map { it.imageName }}"),
        artifactVersion = image.version
      )
    )
  }

  private fun ServerGroupSpec?.withVirtualMachineImage(image: VirtualMachineImage) =
    (this ?: ServerGroupSpec()).run {
      copy(
        launchConfiguration = launchConfiguration.run {
          (this ?: LaunchConfigurationSpec()).copy(image = image)
        }
      )
    }
}
