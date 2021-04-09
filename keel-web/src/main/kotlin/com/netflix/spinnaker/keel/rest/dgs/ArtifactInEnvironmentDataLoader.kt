package com.netflix.spinnaker.keel.rest.dgs

import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.core.api.PublishedArtifactInEnvironment
import com.netflix.spinnaker.keel.graphql.types.DgsArtifactStatusInEnvironment
import com.netflix.spinnaker.keel.graphql.types.DgsArtifactVersionInEnvironment
import com.netflix.spinnaker.keel.persistence.KeelRepository
import org.dataloader.MappedBatchLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Loads all the version data for an artifacts in an environment.
 * This includes details about the version as well as the status in the specified environment.
 */
@DgsDataLoader(name = "artifact-in-environment")
class ArtifactInEnvironmentDataLoader(
  private val keelRepository: KeelRepository
) : MappedBatchLoader<ArtifactAndEnvironment, List<DgsArtifactVersionInEnvironment>> {

  override fun load(keys: MutableSet<ArtifactAndEnvironment>?): CompletionStage<MutableMap<ArtifactAndEnvironment, List<DgsArtifactVersionInEnvironment>>> {
    return CompletableFuture.supplyAsync {
      keys
        ?.associateWith { key ->
          keelRepository
            .getAllVersionsForEnvironment(key.artifact, key.deliveryConfig, key.environmentName)
            .map { it.toDgs() }
            .filter { key.statuses.isEmpty() || key.statuses.contains(it.status) }
        }
        ?.toMutableMap()
        ?: mutableMapOf()
    }
  }
}

//empty list means give me all statuses
data class ArtifactAndEnvironment(
  val artifact: DeliveryArtifact,
  val environmentName: String,
  val deliveryConfig: DeliveryConfig,
  val statuses: List<DgsArtifactStatusInEnvironment> = emptyList()
)

fun PublishedArtifactInEnvironment.toDgs() =
  DgsArtifactVersionInEnvironment(
    version = publishedArtifact.version,
    createdAt = publishedArtifact.createdAt.toString(),
    gitMetadata = if (publishedArtifact.gitMetadata == null){
      null
    } else {
      publishedArtifact.gitMetadata?.toDgs()
    },
    environment = environmentName,
    reference = publishedArtifact.reference,
    status = DgsArtifactStatusInEnvironment.valueOf(status.name)
  )
