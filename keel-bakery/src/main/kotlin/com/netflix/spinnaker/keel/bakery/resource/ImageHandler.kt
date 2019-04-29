package com.netflix.spinnaker.keel.bakery.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.ami.BaseImageCache
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.bakery.api.ImageSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceDiff
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ImageHandler(
  override val objectMapper: ObjectMapper,
  override val normalizers: List<ResourceNormalizer<*>>,
  private val cloudDriver: CloudDriverService,
  private val artifactRepository: ArtifactRepository,
  private val baseImageCache: BaseImageCache,
  private val orcaService: OrcaService,
  private val igorService: ArtifactService
) : ResolvableResourceHandler<ImageSpec, Image> {

  override val apiVersion = SPINNAKER_API_V1.subApi("bakery")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "image",
    "images"
  ) to ImageSpec::class.java

  override fun generateName(spec: ImageSpec): ResourceName =
    ResourceName("bakery:image:${spec.artifactName}")

  override fun desired(resource: Resource<ImageSpec>): Image =
    with(resource.spec) {
      val latestVersion = artifactRepository
        .versions(DeliveryArtifact(artifactName, DEB))
        .first()
      Image(
        baseAmiVersion = baseImageCache.getBaseImage(baseOs, baseLabel),
        // TODO: there must be a canonical way of composing this
        appVersion = latestVersion,
        regions = regions
      )
    }

  override fun current(resource: Resource<ImageSpec>): Image? =
    runBlocking {
      cloudDriver.images("aws", resource.spec.artifactName)
        .await()
        .firstOrNull()
        ?.let {
          // TODO: this is pretty crude to say the least
          val tags = it.tagsByImageId.values.first()!!
          Image(
            tags.getValue("base_ami_version")!!,
            tags.getValue("appversion")!!,
            it.amis.keys
          )
        }
    }

  override fun upsert(
    resource: Resource<ImageSpec>,
    resourceDiff: ResourceDiff<Image>
  ): List<TaskRef> {
    val taskRef = runBlocking {
      val (_, application, version) = resourceDiff.desired.appVersion.let { appVersion ->
        Regex("([\\w_]+)-(.+?)/.*")
          .find(appVersion)
          ?.groupValues
          ?: throw IllegalStateException("Could not parse app version $appVersion")
      }
      val artifact = igorService.getArtifact(application, version)
      log.info("baking new image for {}", resource.spec.artifactName)
      orcaService.orchestrate(
        OrchestrationRequest(
          name = "Bake image for ${resource.spec.artifactName}",
          application = "keel", // TODO: revisit if/when we have a way to tie resources to applications
          description = "Bake image for ${resource.spec.artifactName}",
          job = listOf(
            Job(
              "bake",
              mapOf(
                "amiSuffix" to "",
                "baseOs" to resource.spec.baseOs,
                "baseLabel" to resource.spec.baseLabel.name.toLowerCase(),
                "cloudProviderType" to "aws",
                "package" to resource.spec.artifactName,
                "regions" to resource.spec.regions,
                "storeType" to resource.spec.storeType.name.toLowerCase(),
                "user" to "keel",
                "vmType" to "hvm"
              )
            )
          ),
          trigger = OrchestrationTrigger(
            correlationId = resource.metadata.name.toString(),
            artifacts = listOf(artifact.await())
          )
        )
      )
        .await()
    }
    return listOf(TaskRef(taskRef.ref)) // TODO: wow, this is ugly
  }

  override fun delete(resource: Resource<ImageSpec>) {
    TODO("not implemented")
  }

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }
}

/**
 * The resolved representation of an [ImageSpec].
 */
data class Image(
  val baseAmiVersion: String,
  val appVersion: String,
  val regions: Set<String>
)
