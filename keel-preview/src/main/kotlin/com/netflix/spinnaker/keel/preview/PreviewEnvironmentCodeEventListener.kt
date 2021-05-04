package com.netflix.spinnaker.keel.preview

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.generateId
import com.netflix.spinnaker.keel.api.scm.CommitCreatedEvent
import com.netflix.spinnaker.keel.api.scm.PrCreatedEvent
import com.netflix.spinnaker.keel.api.scm.PrMergedEvent
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter.Companion.DEFAULT_MANIFEST_PATH
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter.ATTACH_PREVIEW_ENVIRONMENTS
import com.netflix.spinnaker.keel.persistence.KeelRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Listens to code events that are relevant to managing preview environments.
 *
 * @see PreviewEnvironmentSpec
 */
@Component
class PreviewEnvironmentCodeEventListener(
  private val repository: KeelRepository,
  private val deliveryConfigImporter: DeliveryConfigImporter,
  private val front50Service: Front50Service,
  private val objectMapper: ObjectMapper
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(PreviewEnvironmentCodeEventListener::class.java) }
  }

  @EventListener(PrCreatedEvent::class)
  fun handlePrCreated(event: PrCreatedEvent) {
    // TODO: implement
  }

  @EventListener(PrMergedEvent::class)
  fun handlePrMerged(event: PrMergedEvent) {
    // TODO: implement
  }

  /**
   * Listens to [CommitCreatedEvent] events to catch those that match the branch filter
   * associated with any [PreviewEnvironmentSpec] definitions across all delivery configs.
   *
   * When a match is found, retrieves the delivery config from the target branch, and generate
   * preview [Environment] definitions matching the spec (including the appropriate name
   * overrides), then store/update the environments in the database, which should cause Keel
   * to start checking/actuating on them.
   */
  @EventListener(CommitCreatedEvent::class)
  fun handleCommitCreated(event: CommitCreatedEvent) {
    val (repoType, projectKey, repoSlug) = event.repoKey.split("/")
      .also {
        if (it.size < 3) {
          log.warn("Ignoring commit event with malformed git repository key: ${event.repoKey}")
          return
        }
      }

    val matchingPreviewEnvironments = repository
      .allDeliveryConfigs(ATTACH_PREVIEW_ENVIRONMENTS)
      .associateWith { deliveryConfig ->
        val appConfig = runBlocking {
          try {
            // TODO: cache application data
            front50Service.applicationByName(deliveryConfig.application)
          } catch (e: Exception) {
            log.error("Error retrieving application ${deliveryConfig.application}: $e", e)
            null
          }
        }

        deliveryConfig.previewEnvironments.filter { previewEnvSpec ->
          repoType.equals(appConfig?.repoType, ignoreCase = true)
            && projectKey.equals(appConfig?.repoProjectKey, ignoreCase = true)
            && repoSlug.equals(appConfig?.repoSlug, ignoreCase = true)
            && previewEnvSpec.branch.matches(event.targetBranch)
        }
      }
      .filterValues { it.isNotEmpty() }

    if (matchingPreviewEnvironments.isEmpty()) {
      log.debug("No delivery configs with matching preview environments found for event: $event")
      return
    }

    log.debug("Processing commit event: $event")
    matchingPreviewEnvironments.forEach { (deliveryConfig, previewEnvSpecs) ->
      log.debug("Importing delivery config for app ${deliveryConfig.application} " +
        "from branch ${event.targetBranch}, commit ${event.commitHash}")

      val newDeliveryConfig = try {
        deliveryConfigImporter.import(
          repoType = repoType,
          projectKey = projectKey,
          repoSlug = repoSlug,
          ref = event.commitHash,
          manifestPath = DEFAULT_MANIFEST_PATH // TODO: allow location of manifest to be configurable
        ).toDeliveryConfig()
      } catch (e: Exception) {
        log.error("Error retrieving delivery config: $e", e)
        // TODO: emit event/metric
        return@forEach
      }

      log.info("Creating/updating preview environments for application ${deliveryConfig.application} " +
        "from branch ${event.targetBranch}")
      createPreviewEnvironments(event, newDeliveryConfig, previewEnvSpecs)
    }
  }

  /**
   * Given a list of [PreviewEnvironmentSpec] whose branch filters match the target branch in the
   * [CommitCreatedEvent], create/update the corresponding preview environments in the database.
   */
  private fun createPreviewEnvironments(
    commitEvent: CommitCreatedEvent,
    deliveryConfig: DeliveryConfig,
    previewEnvSpecs: List<PreviewEnvironmentSpec>
  ) {
    val branchDetail = commitEvent.targetBranch.replace("/", "-")

    previewEnvSpecs.forEach { previewEnvSpec ->
      val baseEnv = deliveryConfig.environments.find { it.name == previewEnvSpec.baseEnvironment }
        ?: error("Environment '${previewEnvSpec.baseEnvironment}' referenced in preview environment spec not found.")

      val previewEnv = baseEnv.copy(
        // if the branch is "feature/abc", and the base environment is "test", then the environment
        // would be named "test-feature-abc"
        name = "${baseEnv.name}-$branchDetail",
        constraints = emptySet(),
        verifyWith = previewEnvSpec.verifyWith,
        notifications = previewEnvSpec.notifications,
        resources = baseEnv.resources.mapNotNull {
          it.toPreviewResource(deliveryConfig, previewEnvSpec, branchDetail)
        }.toSet()
      )

      log.debug("Creating/updating preview environment ${previewEnv.name} for application ${deliveryConfig.application} " +
        "from branch ${commitEvent.targetBranch}")
      try {
        previewEnv.resources.forEach { resource ->
          repository.upsertResource(resource, deliveryConfig.name)
        }
        repository.storeEnvironment(deliveryConfig.name, previewEnv)
      } catch (e: Exception) {
        log.error("Error storing/updating preview environment ${deliveryConfig.application}/${previewEnv.name}: $e", e)
        // TODO: emit event/metric
      }
    }
  }

  /**
   * Converts a [Resource] to its preview environment version, i.e. a resource with the exact same
   * characteristics but with its name/ID modified to include the specified [branchDetail], and with
   * its artifact reference (if applicable) updated to use the artifact matching the [PreviewEnvironmentSpec]
   * branch filter, if available.
   */
  private fun Resource<*>.toPreviewResource(
    deliveryConfig: DeliveryConfig,
    previewEnvSpec: PreviewEnvironmentSpec,
    branchDetail: String
  ): Resource<*>? {
    if (spec !is Monikered) {
      log.debug("Ignoring non-monikered resource ${this.id} since it might conflict with the base environment")
      return null
    }

    val previewResource = (this as Resource<Monikered>)
      .withBranchDetail(branchDetail)
      .run {
        val updatedResource = copy(metadata = metadata.toMutableMap().also { metadata ->
          // this is so the resource ID is updated with the new name (which is in the spec)
          metadata["id"] = generateId(this.kind, this.spec)
        })

        // update artifact reference if applicable to match the branch filter of the preview environment
        if (spec is ArtifactReferenceProvider) {
          updatedResource.withBranchArtifact(deliveryConfig, previewEnvSpec)
        } else {
          updatedResource
        }

        // TODO: for clusters, need to check dependencies that might need renaming to match resources in the
        //  preview environment
      }

    log.debug("Renamed resource ${this.id} to ${previewResource.id} for preview environment")
    return previewResource
  }

  /**
   * Adds the specified [branchDetail] to the [Moniker.detail] field of the [ResourceSpec].
   */
  private fun <T : Monikered> Resource<T>.withBranchDetail(branchDetail: String): Resource<T> =
    copy(spec = objectMapper.convertValue<MutableMap<String, Any?>>(this.spec)
      .let { newSpec ->
        newSpec["moniker"] = with(spec.moniker) {
          copy(detail = (if (detail == null) "" else "${detail}-") + branchDetail)
        }
        objectMapper.convertValue(newSpec, spec::class.java)
      }
    )

  /**
   * Replaces the artifact reference in the resource spec with the one matching the [PreviewEnvironmentSpec] branch
   * filter, if such an artifact is defined in the delivery config.
   */
  private fun Resource<*>.withBranchArtifact(
    deliveryConfig: DeliveryConfig,
    previewEnvSpec: PreviewEnvironmentSpec
  ): Resource<*> {
    val specArtifactReference = (spec as? ArtifactReferenceProvider)
      ?.artifactReference
      ?: return this

    val originalArtifact = deliveryConfig.matchingArtifactByReference(specArtifactReference)
      ?: error("Artifact with reference '${specArtifactReference}' not found in delivery config for ${deliveryConfig.application}")

    val artifactFromBranch = deliveryConfig.artifacts.find { artifact ->
      artifact.type == originalArtifact.type
        && artifact.name == originalArtifact.name
        && artifact.from?.branch == previewEnvSpec.branch
    }

    return if (artifactFromBranch != null) {
      log.debug("Found $artifactFromBranch matching branch filter from preview environment spec ${previewEnvSpec.name}. " +
        "Replacing artifact reference in resource ${this.id}.")
      copy(
        spec = objectMapper.convertValue<MutableMap<String, Any?>>(spec).let { newSpec ->
          val containerSpec = (spec as? TitusClusterSpec)?.container as? ReferenceProvider
          if (containerSpec != null) {
            // TODO: it'd be nice if the titus spec followed the convention
            newSpec["container"] = containerSpec.copy(reference = artifactFromBranch.reference)
          } else {
            newSpec["artifactReference"] = artifactFromBranch.reference
          }
          objectMapper.convertValue(newSpec, spec::class.java)
        }
      )
    } else {
      log.debug("Did not find an artifact matching branch filter from preview environment spec ${previewEnvSpec.name}. " +
        "Using existing artifact reference in resource ${this.id}.")
      this
    }
  }
}