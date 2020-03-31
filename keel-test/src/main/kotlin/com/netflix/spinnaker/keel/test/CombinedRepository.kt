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
  clock: Clock = Clock.systemUTC(),
  deliveryConfigRepository: DeliveryConfigRepository = InMemoryDeliveryConfigRepository(clock),
  artifactRepository: ArtifactRepository = InMemoryArtifactRepository(clock),
  resourceRepository: ResourceRepository = InMemoryResourceRepository(clock)
): CombinedRepository =
  CombinedRepository(deliveryConfigRepository, artifactRepository, resourceRepository, clock, mockk(relaxed = true))

fun combinedMockRepository(
  deliveryConfigRepository: DeliveryConfigRepository = mockk(relaxed = true),
  artifactRepository: ArtifactRepository = mockk(relaxed = true),
  resourceRepository: ResourceRepository = mockk(relaxed = true),
  clock: Clock = Clock.systemUTC()
): CombinedRepository =
  CombinedRepository(deliveryConfigRepository, artifactRepository, resourceRepository, clock, mockk(relaxed = true))
