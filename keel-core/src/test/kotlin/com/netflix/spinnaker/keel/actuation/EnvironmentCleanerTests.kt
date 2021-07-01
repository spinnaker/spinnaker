package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.config.EnvironmentDeletionConfig
import com.netflix.spinnaker.config.EnvironmentDeletionConfig.Companion.DEFAULT_MAX_RESOURCE_DELETION_ATTEMPTS
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDependency
import com.netflix.spinnaker.keel.api.artifacts.branchStartsWith
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.events.MaxResourceDeletionAttemptsReached
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.test.DummyDependentResourceHandler
import com.netflix.spinnaker.keel.test.DummyDependentResourceSpec
import com.netflix.spinnaker.keel.test.DummyLocatableResourceHandler
import com.netflix.spinnaker.keel.test.DummyLocatableResourceSpec
import com.netflix.spinnaker.keel.test.TEST_API_V1
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.dependentResource
import com.netflix.spinnaker.keel.test.locatableResource
import com.netflix.spinnaker.time.MutableClock
import io.mockk.called
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import io.mockk.coEvery as every
import io.mockk.coVerify as verify
import org.springframework.core.env.Environment as SpringEnvironment

class EnvironmentCleanerTests {
  private val deliveryConfigRepository: DeliveryConfigRepository = mockk()
  private val resourceRepository: ResourceRepository = mockk()
  private val springEnv: SpringEnvironment = mockk()
  private val clock = MutableClock()
  private val config = EnvironmentDeletionConfig()
  private val eventPublisher: ApplicationEventPublisher = mockk()
  private val dependentResourceHandler = spyk(DummyLocatableResourceHandler)
  private val locatableResourceHandler = spyk(DummyDependentResourceHandler)
  private val resourceHandlers = listOf(locatableResourceHandler, dependentResourceHandler)
  private val subject = EnvironmentCleaner(
    deliveryConfigRepository = deliveryConfigRepository,
    resourceRepository = resourceRepository,
    resourceHandlers = listOf(locatableResourceHandler, dependentResourceHandler),
    springEnv = springEnv,
    config = config,
    clock = clock,
    eventPublisher = eventPublisher
  )

  private val deliveryConfig = deliveryConfig(locatableResource()).copy(
    previewEnvironments = setOf(
      PreviewEnvironmentSpec(
        branch = branchStartsWith("feature/"),
        baseEnvironment = "test"
      )
    )
  )

  private val locatableResource = locatableResource()
  private val dependentResource = dependentResource(
    dependsOn = setOf(
      ResourceDependency(
        region = locatableResource.spec.locations.regions.first().name,
        name = locatableResource.name,
        kind = locatableResource.kind
      )
    )
  )

  private val environment = Environment(
    name = "test-feature-abc",
    resources = setOf(dependentResource, locatableResource),
    isPreview = true
  ).withMetadata()

  private fun Environment.withMetadata() = apply {
    addMetadata(mapOf(
      "repoKey" to "stash/proj/repo",
      "branch" to "feature/abc",
      "deliveryConfigName" to deliveryConfig.name
    ))
  }

  @BeforeEach
  fun setup() {
    every {
      springEnv.getProperty("keel.environment-deletion.enabled", Boolean::class.java, any())
    } returns true

    every {
      springEnv.getProperty("keel.environment-deletion.dryRun", Boolean::class.java, any())
    } returns false

    every {
      deliveryConfigRepository.deleteEnvironment(deliveryConfig.name, environment.name)
    } just runs

    every {
      resourceRepository.delete(any())
    } just runs

    every {
      resourceRepository.incrementDeletionAttempts(any())
    } just runs

    every {
      resourceRepository.countDeletionAttempts(any())
    } returns 0

    resourceHandlers.forEach { resourceHandler ->
      every {
        resourceHandler.delete(any())
      } returns emptyList()
    }

    every {
      eventPublisher.publishEvent(any<Object>())
    } just runs
  }

  @Test
  fun `nothing is not deleted when feature flag is off`() {
    every {
      springEnv.getProperty("keel.environment-deletion.enabled", Boolean::class.java, any())
    } returns false

    subject.cleanupEnvironment(environment)

    verify(exactly=0) {
      resourceHandlers.forEach { resourceHandler ->
        resourceHandler.delete(any())
      }
      resourceRepository.delete(any())
      deliveryConfigRepository.deleteEnvironment(deliveryConfig.name, environment.name)
    }
  }

  @Test
  fun `nothing is deleted when dry-run is on`() {
    every {
      springEnv.getProperty("keel.environment-deletion.dryRun", Boolean::class.java, any())
    } returns true

    subject.cleanupEnvironment(environment)

    verify(exactly=0) {
      resourceHandlers.forEach { resourceHandler ->
        resourceHandler.delete(any())
      }
      resourceRepository.delete(any())
      deliveryConfigRepository.deleteEnvironment(deliveryConfig.name, environment.name)
    }
  }

  @Test
  fun `the preview environment is not deleted when it still has resources`() {
    subject.cleanupEnvironment(environment)

    verify(exactly=0) {
      deliveryConfigRepository.deleteEnvironment(deliveryConfig.name, environment.name)
    }
  }

  @Test
  fun `the preview environment is deleted when there are no resources left`() {
    subject.cleanupEnvironment(environment.copy(resources = emptySet()).withMetadata())

    verify {
      deliveryConfigRepository.deleteEnvironment(deliveryConfig.name, environment.name)
    }
  }

  @Test
  fun `resources are picked for deletion based on dependency sorting`() {
    subject.cleanupEnvironment(environment)

    val sorted = environment.resourcesSortedByDependencies
    val firstResource = sorted[0] as Resource<Nothing>
    val firstResourceHandler = resourceHandlers.supporting(firstResource.kind)
    val secondResource = sorted[1] as Resource<Nothing>
    val secondResourceHandler = resourceHandlers.supporting(secondResource.kind)

    println(resourceHandlers.joinToString(prefix = "Registered resource handlers: ") { it.supportedKind.kind.toString() })

    verify(exactly = 1) {
      firstResourceHandler.delete(firstResource)
      resourceRepository.delete(firstResource.id)
    }

    verify(exactly = 0) {
      secondResourceHandler.delete(secondResource)
      resourceRepository.delete(secondResource.id)
    }
  }

  @Test
  fun `resource deletion is attempted until maximum allowed retries`() {
    var count = 0
    val resource = environment.resourcesSortedByDependencies.first() as Resource<Nothing>
    val resourceHandler = resourceHandlers.supporting(resource.kind)

    every {
      resourceHandler.delete(resource)
    } throws IllegalStateException("Oh noes!")

    every {
      resourceRepository.incrementDeletionAttempts(resource)
    } answers { count++ }

    every {
      resourceRepository.countDeletionAttempts(resource)
    } answers { count }

    repeat(DEFAULT_MAX_RESOURCE_DELETION_ATTEMPTS) {
      subject.cleanupEnvironment(environment)
      verify {
        resourceHandler.delete(resource)
        resourceRepository.incrementDeletionAttempts(resource)
      }
      verify { eventPublisher wasNot called }
    }

    subject.cleanupEnvironment(environment)
    verify {
      eventPublisher.publishEvent(any<MaxResourceDeletionAttemptsReached>())
    }
  }
}