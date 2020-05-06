package com.netflix.spinnaker.keel.test

import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.CombinedRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryPausedRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import io.mockk.mockk
import java.time.Clock
import org.springframework.context.ApplicationEventPublisher

class InMemoryCombinedRepository(
  override val clock: Clock = Clock.systemUTC(),
  override val deliveryConfigRepository: InMemoryDeliveryConfigRepository = InMemoryDeliveryConfigRepository(clock),
  override val artifactRepository: InMemoryArtifactRepository = InMemoryArtifactRepository(clock),
  override val resourceRepository: InMemoryResourceRepository = InMemoryResourceRepository(clock),
  val pausedRepository: InMemoryPausedRepository = InMemoryPausedRepository(clock),
  eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
) : CombinedRepository(
  deliveryConfigRepository,
  artifactRepository,
  resourceRepository,
  clock,
  eventPublisher
) {
  fun dropAll() {
    deliveryConfigRepository.dropAll()
    artifactRepository.dropAll()
    resourceRepository.dropAll()
    pausedRepository.flush()
  }
}

/**
 * A util for generating a combined repository with in memory implementations for tests
 */
fun combinedInMemoryRepository(
  clock: Clock = Clock.systemUTC(),
  deliveryConfigRepository: InMemoryDeliveryConfigRepository = InMemoryDeliveryConfigRepository(clock),
  artifactRepository: InMemoryArtifactRepository = InMemoryArtifactRepository(clock),
  resourceRepository: InMemoryResourceRepository = InMemoryResourceRepository(clock),
  pausedRepository: InMemoryPausedRepository = InMemoryPausedRepository(clock)
): InMemoryCombinedRepository =
  InMemoryCombinedRepository(clock, deliveryConfigRepository, artifactRepository, resourceRepository, pausedRepository)

fun combinedMockRepository(
  deliveryConfigRepository: DeliveryConfigRepository = mockk(relaxed = true),
  artifactRepository: ArtifactRepository = mockk(relaxed = true),
  resourceRepository: ResourceRepository = mockk(relaxed = true),
  clock: Clock = Clock.systemUTC(),
  publisher: ApplicationEventPublisher = mockk(relaxed = true)
): CombinedRepository =
  CombinedRepository(deliveryConfigRepository, artifactRepository, resourceRepository, clock, publisher)
