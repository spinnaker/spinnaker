package com.netflix.spinnaker.keel.persistence

internal class InMemoryAssetRepositoryTest : AssetRepositoryTests<InMemoryAssetRepository>() {
  override val subject: InMemoryAssetRepository
    get() = InMemoryAssetRepository()
}
