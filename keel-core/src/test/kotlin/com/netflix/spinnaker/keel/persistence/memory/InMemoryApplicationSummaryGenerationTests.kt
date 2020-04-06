package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.persistence.ApplicationSummaryGenerationTests
import java.time.Clock

class InMemoryApplicationSummaryGenerationTests : ApplicationSummaryGenerationTests<InMemoryArtifactRepository>() {
  override fun factory(clock: Clock) = InMemoryArtifactRepository(clock)

  override fun InMemoryArtifactRepository.flush() {
    dropAll()
  }

  private val deliveryConfigRepository = InMemoryDeliveryConfigRepository(
    Clock.systemUTC())

  override fun persist(manifest: DeliveryConfig) {
    deliveryConfigRepository.store(manifest)
  }
}
