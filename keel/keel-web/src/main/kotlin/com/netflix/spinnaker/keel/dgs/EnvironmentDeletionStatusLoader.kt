package com.netflix.spinnaker.keel.dgs

import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.graphql.types.MdEnvironment
import com.netflix.spinnaker.keel.persistence.EnvironmentDeletionRepository
import org.dataloader.MappedBatchLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * [DgsDataLoader] responsible for loading [MdEnvironment.isDeleting] from the database.
 */
@DgsDataLoader(name = EnvironmentDeletionStatusLoader.NAME)
class EnvironmentDeletionStatusLoader(
  private val environmentDeletionRepository: EnvironmentDeletionRepository
) : MappedBatchLoader<Environment, Boolean> {
  companion object {
    const val NAME = "environment-is-deleting"
  }

  override fun load(mdEnvs: MutableSet<Environment>): CompletionStage<MutableMap<Environment, Boolean>> {
    val markedForDeletion = environmentDeletionRepository.bulkGetMarkedForDeletion(mdEnvs)
    return CompletableFuture.supplyAsync {
      markedForDeletion.toMutableMap()
    }
  }
}
