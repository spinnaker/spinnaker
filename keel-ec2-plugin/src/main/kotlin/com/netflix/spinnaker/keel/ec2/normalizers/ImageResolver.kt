package com.netflix.spinnaker.keel.ec2.normalizers

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.NoImageFound
import com.netflix.spinnaker.keel.api.NoImageFoundForRegions
import com.netflix.spinnaker.keel.api.NoImageSatisfiesConstraints
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.VirtualMachineImage
import com.netflix.spinnaker.keel.api.ec2.ImageProvider
import com.netflix.spinnaker.keel.api.ec2.JenkinsImageProvider
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ImageResolver(
  private val dynamicConfigService: DynamicConfigService,
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val artifactRepository: ArtifactRepository,
  private val imageService: ImageService
) : Resolver<ClusterSpec> {

  override val apiVersion: ApiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind: String = "cluster"

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun invoke(resource: Resource<ClusterSpec>): Resource<ClusterSpec> {
    val imageProvider = resource.spec.imageProvider ?: return resource
    val image = runBlocking {
      when (imageProvider) {
        is ArtifactImageProvider -> resolveFromArtifact(resource, imageProvider)
        is JenkinsImageProvider -> resolveFromJenkinsJob(resource, imageProvider)
      }
    }
    return resource.withVirtualMachineImages(image)
  }

  private suspend fun resolveFromArtifact(
    resource: Resource<ClusterSpec>,
    imageProvider: ArtifactImageProvider
  ): NamedImage {
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
      imageService.getLatestNamedImage(
        appVersion = AppVersion.parseName(artifactVersion),
        account = account
      ) ?: throw NoImageFound(artifactVersion)
    } else {
      imageService.getLatestNamedImage(
        packageName = artifact.name,
        account = account
      ) ?: throw NoImageFound(artifact.name)
    }
  }

  private suspend fun resolveFromJenkinsJob(
    resource: Resource<ClusterSpec>,
    imageProvider: JenkinsImageProvider
  ): NamedImage {
    val image = imageService.getNamedImageFromJenkinsInfo(
      imageProvider.packageName,
      dynamicConfigService.getConfig("images.default-account", "test"),
      imageProvider.buildHost,
      imageProvider.buildName,
      imageProvider.buildNumber
    ) ?: throw NoImageFound(imageProvider.packageName)

    log.info("Image found for {}: {}", imageProvider.packageName, image)
    return image
  }

  private fun Resource<ClusterSpec>.withVirtualMachineImages(image: NamedImage): Resource<ClusterSpec> {
    val imageIdByRegion = image
      .amis
      .filterNotNullValues()
      .filterValues { it.isNotEmpty() }
      .mapValues { it.value.first() }
    val missingRegions = spec.locations.regions.map { it.region } - imageIdByRegion.keys
    if (missingRegions.isNotEmpty()) {
      throw NoImageFoundForRegions(image.imageName, missingRegions)
    }

    val overrides = mutableMapOf<String, ServerGroupSpec>()
    overrides.putAll(spec.overrides)
    spec.locations.regions.map { it.region }.forEach { region ->
      overrides[region] = overrides[region].withVirtualMachineImage(VirtualMachineImage(imageIdByRegion.getValue(region), image.appVersion))
    }

    return copy(spec = spec.copy(overrides = overrides))
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
