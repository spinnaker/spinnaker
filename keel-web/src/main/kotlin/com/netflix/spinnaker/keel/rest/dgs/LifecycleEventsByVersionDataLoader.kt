package com.netflix.spinnaker.keel.rest.dgs

import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.graphql.types.DgsLifecycleStep
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.lifecycle.LifecycleStep
import org.dataloader.MappedBatchLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Loads all lifecycle events for a single version of an artifact
 */
@DgsDataLoader(name = "artifact-lifecycle-events-version")
class LifecycleEventsByVersionDataLoader(
  private val lifecycleEventRepository: LifecycleEventRepository
) : MappedBatchLoader<ArtifactAndVersion, List<DgsLifecycleStep>> {
  override fun load(keys: MutableSet<ArtifactAndVersion>?): CompletionStage<MutableMap<ArtifactAndVersion, List<DgsLifecycleStep>>> {
    return CompletableFuture.supplyAsync {
      val result: MutableMap<ArtifactAndVersion, List<DgsLifecycleStep>> = mutableMapOf()
      keys
        ?.map { it.artifact }
        ?.toSet()
        ?.forEach { artifact ->
          val allVersions: List<DgsLifecycleStep> = lifecycleEventRepository
            .getSteps(artifact)
            .map { it.toDgs() }

          val byVersion: Map<String, List<DgsLifecycleStep>> = allVersions
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
  DgsLifecycleStep(
    scope = scope.name,
    type = type.name,
    id = id,
    status = status.name,
    text = text,
    link = link,
    startedAt = startedAt.toString(),
    completedAt = completedAt.toString(),
    artifactVersion = artifactVersion,
  )

data class ArtifactAndVersion(
  val artifact: DeliveryArtifact,
  val version: String
)
