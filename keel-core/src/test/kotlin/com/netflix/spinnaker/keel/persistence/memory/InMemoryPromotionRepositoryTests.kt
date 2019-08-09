package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.PromotionRepositoryTests

class InMemoryPromotionRepositoryTests : PromotionRepositoryTests<InMemoryPromotionRepository>() {
  override fun factory() = InMemoryPromotionRepository()

  override fun InMemoryPromotionRepository.flush() {
    dropAll()
  }
}
