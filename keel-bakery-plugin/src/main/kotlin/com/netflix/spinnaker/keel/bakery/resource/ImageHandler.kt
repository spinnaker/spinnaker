package com.netflix.spinnaker.keel.bakery.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.NoKnownArtifactVersions
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.bakery.api.ImageSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.Image
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.SupportedKind
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher

class ImageHandler(
  private val artifactRepository: ArtifactRepository,
  private val baseImageCache: BaseImageCache,
  private val cloudDriver: CloudDriverService,
  private val orcaService: OrcaService,
  private val igorService: ArtifactService,
  private val imageService: ImageService,
  private val publisher: ApplicationEventPublisher,
  objectMapper: ObjectMapper,
  resolvers: List<Resolver<*>>
) : ResourceHandler<ImageSpec, Image>(objectMapper, resolvers) {

  override val supportedKind =
    SupportedKind(SPINNAKER_API_V1.subApi("bakery"), "image", ImageSpec::class.java)

  override suspend fun toResolvedType(resource: Resource<ImageSpec>): Image =
    with(resource) {
      val artifact = DebianArtifact(spec.artifactName)
      val latestVersion = artifact.findLatestVersion(resource.spec.artifactStatuses)
      val baseImage = baseImageCache.getBaseImage(spec.baseOs, spec.baseLabel)
      val baseAmi = findBaseAmi(baseImage, resource.serviceAccount)
      Image(
        baseAmiVersion = baseAmi,
        appVersion = latestVersion,
        regions = spec.regions
      )
    }

  override suspend fun current(resource: Resource<ImageSpec>): Image? =
    with(resource) {
      imageService.getLatestImageWithAllRegions(spec.artifactName, "test", resource.spec.regions.toList())?.let {
        it.copy(regions = it.regions.intersect(resource.spec.regions))
      }
    }

  /**
   * First checks our repo, and if a version isn't found checks igor.
   */
  private fun DeliveryArtifact.findLatestVersion(statuses: List<ArtifactStatus>): String {
    try {
      val knownVersion = artifactRepository
        .versions(this, statuses)
        .firstOrNull()
      if (knownVersion != null) {
        return knownVersion
      }
    } catch (e: NoSuchArtifactException) {
      if (!artifactRepository.isRegistered(name, type)) {
        // we clearly care about this artifact, let's register it.
        publisher.publishEvent(ArtifactRegisteredEvent(this))
      }
    }

    // even though the artifact isn't registered we should grab the latest version to use
    return runBlocking {
      igorService
        .getVersions(name, statuses.map { it.toString() })
        .firstOrNull()
        ?.let {
          "$name-$it"
        }
    } ?: throw NoKnownArtifactVersions(this)
  }

  override suspend fun upsert(
    resource: Resource<ImageSpec>,
    resourceDiff: ResourceDiff<Image>
  ): List<Task> {
    val appVersion = AppVersion.parseName(resourceDiff.desired.appVersion)
    val packageName = appVersion.packageName
    val version = resourceDiff.desired.appVersion.substringAfter("$packageName-")
    val artifact = Artifact.builder()
      .type("DEB")
      .customKind(false)
      .name(resource.spec.artifactName)
      .version(version)
      .location("rocket")
      .reference("/${packageName}_${version}_all.deb")
      .metadata(mapOf())
      .provenance("n/a")
      .build()

    log.info("baking new image for {}", resource.spec.artifactName)
    val description = "Bake ${resourceDiff.desired.appVersion}"
    val taskRef = orcaService.orchestrate(
      resource.serviceAccount,
      OrchestrationRequest(
        name = description,
        application = resource.application,
        description = description,
        job = listOf(
          Job(
            "bake",
            mapOf(
              "amiSuffix" to "",
              "baseOs" to resource.spec.baseOs,
              "baseLabel" to resource.spec.baseLabel.name.toLowerCase(),
              "cloudProviderType" to "aws",
              "package" to artifact.reference.substringAfterLast("/"),
              "regions" to resource.spec.regions,
              "storeType" to resource.spec.storeType.name.toLowerCase(),
              "user" to "keel",
              "vmType" to "hvm"
            )
          )
        ),
        trigger = OrchestrationTrigger(
          correlationId = resource.id.toString(),
          notifications = emptyList(),
          artifacts = listOf(artifact)
        )
      )
    )
    return listOf(Task(id = taskRef.taskId, name = description)) // TODO: wow, this is ugly
  }

  override suspend fun actuationInProgress(resource: Resource<ImageSpec>): Boolean =
    orcaService
      .getCorrelatedExecutions(resource.id.toString())
      .isNotEmpty()

  private suspend fun findBaseAmi(baseImage: String, serviceAccount: String): String {
    return cloudDriver.namedImages(serviceAccount, baseImage, "test")
      .lastOrNull()
      ?.let { namedImage ->
        val tags = namedImage
          .tagsByImageId
          .values
          .first { it?.containsKey("base_ami_version") ?: false }
        if (tags != null) {
          tags.getValue("base_ami_version")!!
        } else {
          null
        }
      } ?: throw BaseAmiNotFound(baseImage)
  }

  private fun ResourceDiff<Image>.isRegionOnly(): Boolean =
    current != null && (current as Image).regions.size != desired.regions.size
}

class BaseAmiNotFound(baseImage: String) : RuntimeException("Could not find a base AMI for base image $baseImage")
