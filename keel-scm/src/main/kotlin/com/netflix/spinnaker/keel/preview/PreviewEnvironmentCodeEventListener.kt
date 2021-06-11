package com.netflix.spinnaker.keel.preview

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Dependency
import com.netflix.spinnaker.keel.api.Dependent
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.generateId
import com.netflix.spinnaker.keel.api.scm.CodeEvent
import com.netflix.spinnaker.keel.api.scm.CommitCreatedEvent
import com.netflix.spinnaker.keel.api.scm.PrCreatedEvent
import com.netflix.spinnaker.keel.api.scm.PrMergedEvent
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter.Companion.DEFAULT_MANIFEST_PATH
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter.ATTACH_PREVIEW_ENVIRONMENTS
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.scm.DELIVERY_CONFIG_RETRIEVAL_ERROR
import com.netflix.spinnaker.keel.scm.DELIVERY_CONFIG_RETRIEVAL_SUCCESS
import com.netflix.spinnaker.keel.scm.matchesApplicationConfig
import com.netflix.spinnaker.keel.scm.metricTags
import com.netflix.spinnaker.keel.telemetry.recordDuration
import com.netflix.spinnaker.keel.telemetry.safeIncrement
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Listens to code events that are relevant to managing preview environments.
 *
 * @see PreviewEnvironmentSpec
 */
@Component
class PreviewEnvironmentCodeEventListener(
  private val repository: KeelRepository,
  private val deliveryConfigImporter: DeliveryConfigImporter,
  private val front50Cache: Front50Cache,
  private val objectMapper: ObjectMapper,
  private val springEnv: Environment,
  private val spectator: Registry,
  private val clock: Clock
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(PreviewEnvironmentCodeEventListener::class.java) }
    internal const val CODE_EVENT_COUNTER = "previewEnvironments.codeEvent.count"
    internal val APPLICATION_RETRIEVAL_ERROR = listOf("type" to "application.retrieval", "status" to "error")
    internal val DELIVERY_CONFIG_NOT_FOUND = "type" to "deliveryConfig.notFound"
    internal val PREVIEW_ENVIRONMENT_UPSERT_ERROR = listOf("type" to "upsert", "status" to "error")
    internal val PREVIEW_ENVIRONMENT_UPSERT_SUCCESS = listOf("type" to "upsert", "status" to "success")
    internal const val COMMIT_HANDLING_DURATION = "previewEnvironments.commitHandlingDuration"
  }

  private val enabled: Boolean
    get() = springEnv.getProperty("keel.previewEnvironments.enabled", Boolean::class.java, true)

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
    if (!enabled) {
      log.debug("Preview environments disabled by feature flag. Ignoring commit event: $event")
      return
    }

    val startTime = clock.instant()

    val matchingPreviewEnvironments = repository
      .allDeliveryConfigs(ATTACH_PREVIEW_ENVIRONMENTS)
      .associateWith { deliveryConfig ->
        val appConfig = runBlocking {
          try {
            front50Cache.applicationByName(deliveryConfig.application)
          } catch (e: Exception) {
            log.error("Error retrieving application ${deliveryConfig.application}: $e")
            event.emitCounterMetric(CODE_EVENT_COUNTER, APPLICATION_RETRIEVAL_ERROR, deliveryConfig.application)
            null
          }
        }

        deliveryConfig.previewEnvironments.filter { previewEnvSpec ->
          event.matchesApplicationConfig(appConfig)  && previewEnvSpec.branch.matches(event.targetBranch)
        }
      }
      .filterValues { it.isNotEmpty() }

    if (matchingPreviewEnvironments.isEmpty()) {
      log.debug("No delivery configs with matching preview environments found for event: $event")
      event.emitCounterMetric(CODE_EVENT_COUNTER, DELIVERY_CONFIG_NOT_FOUND)
      return
    }

    log.debug("Processing commit event: $event")
    matchingPreviewEnvironments.forEach { (deliveryConfig, previewEnvSpecs) ->
      log.debug("Importing delivery config for app ${deliveryConfig.application} " +
        "from branch ${event.targetBranch}, commit ${event.commitHash}")

      val newDeliveryConfig = try {
        deliveryConfigImporter.import(
          commitEvent = event,
          manifestPath = DEFAULT_MANIFEST_PATH // TODO: allow location of manifest to be configurable
        ).toDeliveryConfig().also {
          event.emitCounterMetric(CODE_EVENT_COUNTER, DELIVERY_CONFIG_RETRIEVAL_SUCCESS, deliveryConfig.application)
        }
      } catch (e: Exception) {
        log.error("Error retrieving delivery config: $e", e)
        event.emitCounterMetric(CODE_EVENT_COUNTER, DELIVERY_CONFIG_RETRIEVAL_ERROR, deliveryConfig.application)
        return@forEach
      }

      log.info("Creating/updating preview environments for application ${deliveryConfig.application} " +
        "from branch ${event.targetBranch}")
      createPreviewEnvironments(event, newDeliveryConfig, previewEnvSpecs)
      event.emitDurationMetric(COMMIT_HANDLING_DURATION, startTime, deliveryConfig.application)
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
        isPreview = true,
        constraints = emptySet(),
        verifyWith = previewEnvSpec.verifyWith,
        notifications = previewEnvSpec.notifications,
        resources = baseEnv.resources.mapNotNull { res ->
          (res as? Resource<Monikered>)
            ?.toPreviewResource(deliveryConfig, previewEnvSpec, branchDetail)
            .also {
              if (it == null) {
                log.debug("Ignoring non-monikered resource ${res.id} since it might conflict with the base environment")
              }
            }
        }.toSet(),
        metadata = baseEnv.metadata + mapOf(
          "branch" to commitEvent.targetBranch,
          "pullRequestId" to commitEvent.pullRequestId
        )
      )

      log.debug("Creating/updating preview environment ${previewEnv.name} for application ${deliveryConfig.application} " +
        "from branch ${commitEvent.targetBranch}")
      try {
        // TODO: run all these within a single transaction
        previewEnv.resources.forEach { resource ->
          repository.upsertResource(resource, deliveryConfig.name)
        }
        repository.storeEnvironment(deliveryConfig.name, previewEnv)
        commitEvent.emitCounterMetric(CODE_EVENT_COUNTER, PREVIEW_ENVIRONMENT_UPSERT_SUCCESS, deliveryConfig.application)
      } catch (e: Exception) {
        log.error("Error storing/updating preview environment ${deliveryConfig.application}/${previewEnv.name}: $e", e)
        commitEvent.emitCounterMetric(CODE_EVENT_COUNTER, PREVIEW_ENVIRONMENT_UPSERT_ERROR, deliveryConfig.application)
      }
    }
  }

  /**
   * Converts a [Resource] to its preview environment version, i.e. a resource with the exact same
   * characteristics but with its name/ID modified to include the specified [branchDetail], and with
   * its artifact reference (if applicable) updated to use the artifact matching the [PreviewEnvironmentSpec]
   * branch filter, if available.
   */
  private fun <T: Monikered> Resource<T>.toPreviewResource(
    deliveryConfig: DeliveryConfig,
    previewEnvSpec: PreviewEnvironmentSpec,
    branchDetail: String
  ): Resource<T>? {
    // start by adding the branch detail to the moniker/name/id
    var previewResource = withBranchDetail(branchDetail)

    // update artifact reference if applicable to match the branch filter of the preview environment
    if (spec is ArtifactReferenceProvider) {
      previewResource = previewResource.withBranchArtifact(deliveryConfig, previewEnvSpec)
    }

    // update dependency names that are part of the preview environment and so have new names
    if (spec is Dependent) {
      previewResource = previewResource.withDependenciesRenamed(deliveryConfig, previewEnvSpec, branchDetail)
    }

    log.debug("Renamed resource ${this.id} to ${previewResource.id} for preview environment")
    return previewResource
  }

  /**
   * Adds the specified [branchDetail] to the [Moniker.detail] field of the [ResourceSpec],
   * and updates the resource ID to match.
   */
  private fun <T : Monikered> Resource<T>.withBranchDetail(branchDetail: String): Resource<T> =
    copy(spec = objectMapper.convertValue<MutableMap<String, Any?>>(this.spec)
      .let { newSpec ->
        newSpec["moniker"] = with(spec.moniker) {
          copy(detail = (if (detail == null) "" else "${detail}-") + branchDetail)
        }
        objectMapper.convertValue(newSpec, spec::class.java)
      }
    ).run {
      val newId = generateId(kind, spec).let {
        // account for the case where the ID is not synthesized from the moniker
        if (!it.contains(branchDetail)) "$it-$branchDetail" else it
      }
      copy(metadata = mapOf(
        // this is so the resource ID is updated with the new name (which is in the spec)
        "id" to newId,
        "application" to application
      ))
    }

  /**
   * Replaces the artifact reference in the resource spec with the one matching the [PreviewEnvironmentSpec] branch
   * filter, if such an artifact is defined in the delivery config.
   */
  private fun <T : Monikered> Resource<T>.withBranchArtifact(
    deliveryConfig: DeliveryConfig,
    previewEnvSpec: PreviewEnvironmentSpec
  ): Resource<T> {
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

  /**
   * Adds the specified [branchDetail] to the [Moniker.detail] field of the [ResourceSpec].
   *
   * Limitation: this method supports renaming only resources whose specs are [Monikered].
   */
  private fun <T : Monikered> Resource<T>.withDependenciesRenamed(
    deliveryConfig: DeliveryConfig,
    previewEnvSpec: PreviewEnvironmentSpec,
    branchDetail: String
  ): Resource<T> {
    val baseEnvironment = deliveryConfig.findBaseEnvironment(previewEnvSpec)

    val updatedSpec = if (spec is Dependent) {
      val renamedDeps = (spec as Dependent).dependsOn.map { dep ->
        val candidate = baseEnvironment.resources.find { it.spec is Monikered && it.named(dep.name) }
        if (candidate != null) {
          // special case for security group named after the app which is always included by default :-/
          if (candidate.kind.kind.contains("security-group") && candidate.named(application)) {
            log.debug("Skipping dependency rename for default security group $application in resource ${this.name}")
            dep
          } else {
            val newName = (candidate as Resource<Monikered>).withBranchDetail(branchDetail).name
            log.debug("Renaming ${dep.type} dependency ${candidate.name} to $newName in resource ${this.name}")
            Dependency(dep.type, dep.region, newName)
          }
        } else {
          dep
        }
      }.toSet()
      spec.withDependencies(spec::class, renamedDeps)
    } else {
      spec
    }

    return copy(spec = updatedSpec as T)
  }

  private fun DeliveryConfig.findBaseEnvironment(previewEnvSpec: PreviewEnvironmentSpec) =
    environments.find { it.name == previewEnvSpec.baseEnvironment }
      ?: error("Environment '${previewEnvSpec.baseEnvironment}' referenced in preview environment spec not found.")

  private fun Resource<*>.named(name: String) =
    this.name == name

  private fun CodeEvent.emitCounterMetric(metric: String, extraTags: Collection<Pair<String, String>>, application: String? = null) =
    spectator.counter(metric, metricTags(application, extraTags) ).safeIncrement()

  private fun CodeEvent.emitCounterMetric(metric: String, extraTag: Pair<String, String>, application: String? = null) =
    spectator.counter(metric, metricTags(application, setOf(extraTag)) ).safeIncrement()

  private fun CodeEvent.emitDurationMetric(metric: String, startTime: Instant, application: String? = null) =
    spectator.recordDuration(metric, clock, startTime, metricTags(application))
}
