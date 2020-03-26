package com.netflix.spinnaker.keel.test

import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.CombinedRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import io.mockk.mockk
import java.time.Clock

/**
 * A util for generating a combined repository with in memory implementations for tests
 */
fun combinedInMemoryRepository(
  deliveryConfigRepository: DeliveryConfigRepository = InMemoryDeliveryConfigRepository(),
  artifactRepository: ArtifactRepository = InMemoryArtifactRepository(),
  resourceRepository: ResourceRepository = InMemoryResourceRepository()
): CombinedRepository =
  CombinedRepository(deliveryConfigRepository, artifactRepository, resourceRepository, Clock.systemUTC(), mockk(relaxed = true))

fun combinedMockRepository(
  deliveryConfigRepository: DeliveryConfigRepository = mockk(relaxed = true),
  artifactRepository: ArtifactRepository = mockk(relaxed = true),
  resourceRepository: ResourceRepository = mockk(relaxed = true)
): CombinedRepository =
  CombinedRepository(deliveryConfigRepository, artifactRepository, resourceRepository, Clock.systemUTC(), mockk(relaxed = true))
