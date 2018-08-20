package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.fingerprint
import com.netflix.spinnaker.keel.persistence.AssetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AssetProcessor(
  private val repository: AssetRepository,
  private val assetService: AssetService
) {
  private val log = LoggerFactory.getLogger(javaClass)

  // TODO: coroutine
  fun validateSubTree(id: AssetId): Set<AssetId> {
    val desired = repository.get(id)
    if (desired == null) {
      log.error("{}: Not found", id)
      return emptySet()
    } else {
      val invalidAssetIds = mutableSetOf<AssetId>()
      log.info("{}: Validating state", id)
      assetService
        .current(desired)
        ?.also { current ->
          if (desired.fingerprint == current.fingerprint) {
            log.debug("{}: Current state valid", id)
          } else {
            // mark as in diff state
            log.info("{}: Current state invalid", id)
            invalidAssetIds.add(id)
          }
          repository.dependents(id).forEach { dependentId ->
            log.debug("{}: Validating dependent asset {}", id, dependentId)
            validateSubTree(dependentId).also { them ->
              invalidAssetIds.addAll(them)
            }
          }
        }
      return invalidAssetIds
    }
  }
}
