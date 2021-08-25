package com.netflix.spinnaker.keel.dgs

import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.graphql.types.MdLifecycleEventScope
import com.netflix.spinnaker.keel.graphql.types.MdLifecycleEventStatus
import com.netflix.spinnaker.keel.graphql.types.MdLifecycleEventType
import com.netflix.spinnaker.keel.graphql.types.MdLifecycleStep
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.lifecycle.LifecycleStep
import org.dataloader.MappedBatchLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Loads all lifecycle events for a single version of an artifact
 */
@DgsDataLoader(name = LifecycleEventsByVersionDataLoader.Descriptor.name)
class LifecycleEventsByVersionDataLoader(
  private val lifecycleEventRepository: LifecycleEventRepository
) : MappedBatchLoader<ArtifactAndVersion, List<MdLifecycleStep>> {

  object Descriptor {
    const val name = "artifact-lifecycle-events-version"
  }

  override fun load(keys: MutableSet<ArtifactAndVersion>): CompletionStage<MutableMap<ArtifactAndVersion, List<MdLifecycleStep>>> {
    return CompletableFuture.supplyAsync {
      val result: MutableMap<ArtifactAndVersion, List<MdLifecycleStep>> = mutableMapOf()
      keys
        .map { it.artifact }
        .toSet()
        .forEach { artifact ->
          val allVersions: List<MdLifecycleStep> = lifecycleEventRepository
            .getSteps(artifact)
            .map { it.toDgs() }

          val byVersion: Map<String, List<MdLifecycleStep>> = allVersions
            .filter { it.artifactVersion != null }
            .groupBy { it.artifactVersion!! }

          result.putAll(
            byVersion.mapKeys { entry ->
              ArtifactAndVersion(artifact, entry.key)
            }
          )
        }
      result
    }
  }
}

fun LifecycleStep.toDgs() =
  MdLifecycleStep(
    scope = MdLifecycleEventScope.valueOf(scope.name),
    type = MdLifecycleEventType.valueOf(type.name),
    id = id,
    status = MdLifecycleEventStatus.valueOf(status.name),
    text = text,
    link = link,
    startedAt = startedAt,
    completedAt = completedAt,
    artifactVersion = artifactVersion,
  )

data class ArtifactAndVersion(
  val artifact: DeliveryArtifact,
  val version: String
)
