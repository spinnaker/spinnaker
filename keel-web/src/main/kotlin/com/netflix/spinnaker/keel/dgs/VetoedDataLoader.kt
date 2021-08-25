package com.netflix.spinnaker.keel.dgs

import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.spinnaker.keel.core.api.ArtifactVersionVetoData
import com.netflix.spinnaker.keel.graphql.types.MdVersionVeto
import com.netflix.spinnaker.keel.persistence.KeelRepository
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoaderWithContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Loads all constraint states for the given versions
 */
@DgsDataLoader(name = VetoedDataLoader.Descriptor.name)
class VetoedDataLoader(
  private val keelRepository: KeelRepository,
) : MappedBatchLoaderWithContext<EnvironmentArtifactAndVersion, MdVersionVeto> {

  object Descriptor {
    const val name = "artifact-version-vetoed"
  }

  override fun load(keys: MutableSet<EnvironmentArtifactAndVersion>, environment: BatchLoaderEnvironment):
    CompletionStage<MutableMap<EnvironmentArtifactAndVersion, MdVersionVeto>> {
    val context: ApplicationContext = DgsContext.getCustomContext(environment)
    return CompletableFuture.supplyAsync {
      val results: MutableMap<EnvironmentArtifactAndVersion, MdVersionVeto> = mutableMapOf()
      val vetoed = keelRepository.vetoedEnvironmentVersions(context.getConfig())

      vetoed.forEach { envArtifact ->
        envArtifact.versions.map { version ->
          results.put(
            EnvironmentArtifactAndVersion(environmentName = envArtifact.targetEnvironment, artifactReference = envArtifact.artifact.reference, version = version.version),
            version.toDgs()
          )
        }
      }
      results
    }
  }
}

fun ArtifactVersionVetoData.toDgs() =
  MdVersionVeto(vetoedBy = vetoedBy, vetoedAt = vetoedAt, comment = comment)
