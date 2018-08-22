package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import com.netflix.spinnaker.q.MessageHandler
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConvergeAssetHandler(
  private val repository: AssetRepository,
  override val queue: Queue,
  private val assetService: AssetService,
  private val vetoService: VetoService
) : MessageHandler<ConvergeAsset> {

  override val messageType = ConvergeAsset::class.java
  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(message: ConvergeAsset) {
    repository.get(message.id)?.also { asset ->
      val outdatedDependencies = asset.outdatedDependencies
      if (outdatedDependencies.isEmpty()) {
        if (vetoService.allow(asset)) {
          log.info("{} : requesting convergence")
          assetService.converge(asset)
        } else {
          log.info("{} : convergence was vetoed")
        }
      } else {
        if (log.isInfoEnabled) {
          log.info("{} : not converging as outdated dependencies were found:", asset.id)
          outdatedDependencies.forEach { (id, state) ->
            log.info(" • {} : {}", state, id)
          }
        }
      }
    }
  }

  private val Asset.outdatedDependencies: Collection<Pair<AssetId, AssetState>>
    get() =
      dependsOn.flatMap { dependencyId ->
        val state = repository.lastKnownState(dependencyId)?.first ?: Unknown
        return if (state == Ok) {
          repository.get(dependencyId)?.outdatedDependencies
            ?: listOf(dependencyId to Unknown)
        } else {
          listOf(dependencyId to state)
        }
      }

  private val AssetId.isUpToDate: Boolean
    get() = repository.lastKnownState(this)?.first == Ok
}
