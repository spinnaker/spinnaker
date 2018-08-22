package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledAssetChecker(
  private val repository: AssetRepository,
  private val queue: Queue
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @Scheduled(fixedDelayString = "\${check.cycle.frequency.ms:3600000}")
  fun runCheckCycle() {
    log.info("Starting check cycle")
    repository.rootAssets {
      queue.push(ValidateAssetTree(it.id))
    }
  }
}
