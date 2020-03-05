package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType.deb
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.VirtualMachineImage
import com.netflix.spinnaker.keel.api.ec2.JenkinsImageProvider
import com.netflix.spinnaker.keel.api.ec2.ReferenceArtifactImageProvider
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.clouddriver.model.baseImageVersion
import com.netflix.spinnaker.keel.ec2.NoImageFound
import com.netflix.spinnaker.keel.ec2.NoImageFoundForRegions
import com.netflix.spinnaker.keel.ec2.NoImageSatisfiesConstraints
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
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

  override val supportedKind = SPINNAKER_EC2_API_V1.qualify("cluster")

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  data class VersionedNamedImage(
    val namedImage: NamedImage,
    val artifact: DeliveryArtifact?,
    val version: String?
  )

  override fun invoke(resource: Resource<ClusterSpec>): Resource<ClusterSpec> {
    val imageProvider = resource.spec.imageProvider ?: return resource
    val image = runBlocking {
      when (imageProvider) {
        is ReferenceArtifactImageProvider -> resolveFromReference(resource, imageProvider)
        // todo eb: artifact provider is here for backwards compatibility. Remove?
        is ArtifactImageProvider -> resolveFromArtifact(resource, imageProvider.deliveryArtifact as DebianArtifact)
        is JenkinsImageProvider -> resolveFromJenkinsJob(resource, imageProvider)
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
    val artifact = deliveryConfig.artifacts.find { it.reference == imageProvider.reference && it.type == deb }
      ?: throw NoMatchingArtifactException(deliveryConfig.name, deb, imageProvider.reference)

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
    val image = imageService.getLatestNamedImageWithAllRegionsForAppVersion(
      appVersion = AppVersion.parseName(artifactVersion),
      account = account,
      regions = regions
    ) ?: throw NoImageFoundForRegions(artifactVersion, regions)

    return VersionedNamedImage(image, artifact, artifactVersion)
  }

  private suspend fun resolveFromJenkinsJob(
    resource: Resource<ClusterSpec>,
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
    return VersionedNamedImage(image, null, null)
  }

  private fun Resource<ClusterSpec>.withVirtualMachineImages(image: VersionedNamedImage): Resource<ClusterSpec> {
    val imageIdByRegion = image
      .namedImage
      .amis
      .filterNotNullValues()
      .filterValues { it.isNotEmpty() }
      .mapValues { it.value.first() }
    val missingRegions = spec.locations.regions.map { it.name } - imageIdByRegion.keys
    if (missingRegions.isNotEmpty()) {
      throw NoImageFoundForRegions(image.namedImage.imageName, missingRegions)
    }

    val overrides = mutableMapOf<String, ServerGroupSpec>()
    overrides.putAll(spec.overrides)
    spec.locations.regions.map { it.name }.forEach { region ->
      overrides[region] = overrides[region]
        .withVirtualMachineImage(
          VirtualMachineImage(
            imageIdByRegion.getValue(region),
            image.namedImage.appVersion,
            image.namedImage.baseImageVersion
          )
        )
    }

    return copy(spec = spec.copy(
      overrides = overrides,
      deliveryArtifact = image.artifact,
      artifactVersion = image.version))
  }

  private fun ServerGroupSpec?.withVirtualMachineImage(image: VirtualMachineImage) =
    (this ?: ServerGroupSpec()).run {
      copy(launchConfiguration = launchConfiguration.run {
        (this ?: LaunchConfigurationSpec()).copy(image = image)
      })
    }

  @Suppress("UNCHECKED_CAST")
  private fun <K, V> Map<out K, V?>.filterNotNullValues(): Map<K, V> =
    filterValues { it != null } as Map<K, V>

  private inline fun <reified T> DynamicConfigService.getConfig(configName: String, defaultValue: T) =
    getConfig(T::class.java, configName, defaultValue)
}
