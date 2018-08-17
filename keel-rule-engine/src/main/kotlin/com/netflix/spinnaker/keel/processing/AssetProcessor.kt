package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.fingerprint
import com.netflix.spinnaker.keel.persistence.AssetRepository
import org.springframework.stereotype.Component

@Component
class AssetProcessor(
  private val repository: AssetRepository,
  private val assetService: AssetService,
  private val vetoService: VetoService
) {
  fun checkAsset(id: AssetId) {
    repository.get(id)?.also { desired ->
      assetService.current(desired)?.also { current ->
        if (desired.fingerprint != current.fingerprint) {
          if (vetoService.allow(desired)) {
            assetService.converge(desired)
          }
        }
      }
    }
  }
}
