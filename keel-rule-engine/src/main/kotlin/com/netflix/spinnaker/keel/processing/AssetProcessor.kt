package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.fingerprint
import com.netflix.spinnaker.keel.persistence.AssetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AssetProcessor(
  private val repository: AssetRepository,
  private val assetService: AssetService,
  private val vetoService: VetoService
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun checkAsset(id: AssetId) {
    repository.get(id)?.also { desired ->
      log.info("{}: Validating state", desired.id)
      assetService.current(desired)?.also { current ->
        if (desired.fingerprint != current.fingerprint) {
          log.info("{}: Current state does not match: seeking permission to converge", desired.id)
          if (vetoService.allow(desired)) {
            log.info("{}: Converging", desired.id)
            assetService.converge(desired)
          } else {
            log.warn("{}: Convergence denied", desired.id)
          }
        } else {
          log.debug("{}: Current state valid", desired.id)
        }
      }
    }
  }
}
