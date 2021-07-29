package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.config.EnvironmentDeletionConfig
import com.netflix.spinnaker.config.EnvironmentDeletionConfig.Companion.DEFAULT_MAX_RESOURCE_DELETION_ATTEMPTS
import com.netflix.spinnaker.keel.api.Dependency
import com.netflix.spinnaker.keel.api.DependencyType.GENERIC_RESOURCE
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.TaskExecution
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.api.TaskStatus.RUNNING
import com.netflix.spinnaker.keel.api.TaskStatus.SUCCEEDED
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.branchStartsWith
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.events.MaxResourceDeletionAttemptsReached
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceDeletionLaunched
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ACTUATING
import com.netflix.spinnaker.keel.persistence.ResourceStatus.DELETING
import com.netflix.spinnaker.keel.persistence.ResourceStatus.HAPPY
import com.netflix.spinnaker.keel.services.ResourceStatusService
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
import io.mockk.clearMocks
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
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
  private val resourceStatusService: ResourceStatusService = mockk()
  private val taskLauncher: TaskLauncher = mockk()
  private val subject = EnvironmentCleaner(
    deliveryConfigRepository = deliveryConfigRepository,
    resourceRepository = resourceRepository,
    resourceHandlers = listOf(locatableResourceHandler, dependentResourceHandler),
    springEnv = springEnv,
    config = config,
    clock = clock,
    eventPublisher = eventPublisher,
    resourceStatusService = resourceStatusService,
    taskLauncher = taskLauncher
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
      Dependency(
        type = GENERIC_RESOURCE,
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

    every {
      resourceRepository.eventHistory(any(), any())
    } returns listOf(ResourceActuationLaunched(locatableResource, "plugin", emptyList()))

    resourceHandlers.forEach { resourceHandler ->
      every {
        resourceHandler.delete(any())
      } returns emptyList()
    }

    every {
      eventPublisher.publishEvent(any<Object>())
    } just runs

    every {
      resourceStatusService.getStatus(any())
    } returns HAPPY

    every {
      taskLauncher.getTaskExecution(any())
    } returns object : TaskExecution {
      override val id: String = "1234"
      override val name: String = "Task 1234"
      override val application: String = "fnord"
      override val startTime: Instant? = null
      override val endTime: Instant? = null
      override val status: TaskStatus = RUNNING
    }
  }

  @Test
  fun `nothing is not deleted when feature flag is off`() {
    every {
      springEnv.getProperty("keel.environment-deletion.enabled", Boolean::class.java, any())
    } returns false

    runBlocking { subject.cleanupEnvironment(environment) }

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

    runBlocking { subject.cleanupEnvironment(environment) }

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
    runBlocking { subject.cleanupEnvironment(environment) }

    verify(exactly=0) {
      deliveryConfigRepository.deleteEnvironment(deliveryConfig.name, environment.name)
    }
  }

  @Test
  fun `the preview environment is deleted when there are no resources left`() {
    runBlocking {
      subject.cleanupEnvironment(environment.copy(resources = emptySet()).withMetadata())
    }

    verify {
      deliveryConfigRepository.deleteEnvironment(deliveryConfig.name, environment.name)
    }
  }

  @Test
  fun `resources are picked for deletion based on dependency sorting`() {
    runBlocking { subject.cleanupEnvironment(environment) }

    val sorted = environment.resourcesSortedByDependencies
    val firstResource = sorted[0] as Resource<Nothing>
    val firstResourceHandler = resourceHandlers.supporting(firstResource.kind)
    val secondResource = sorted[1] as Resource<Nothing>
    val secondResourceHandler = resourceHandlers.supporting(secondResource.kind)

    println(resourceHandlers.joinToString(prefix = "Registered resource handlers: ") { it.supportedKind.kind.toString() })

    verify(exactly = 1) {
      firstResourceHandler.delete(firstResource)
    }

    verify(exactly = 0) {
      secondResourceHandler.delete(secondResource)
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
      runBlocking { subject.cleanupEnvironment(environment) }
      verify {
        resourceHandler.delete(resource)
        resourceRepository.incrementDeletionAttempts(resource)
      }
      verify { eventPublisher wasNot called }
    }

    runBlocking { subject.cleanupEnvironment(environment) }
    verify {
      eventPublisher.publishEvent(any<MaxResourceDeletionAttemptsReached>())
    }
  }

  @Test
  fun `resource deletion is skipped if it's already currently deleting`() {
    val resource = environment.resourcesSortedByDependencies.first() as Resource<Nothing>
    val resourceHandler = resourceHandlers.supporting(resource.kind)

    every {
      resourceStatusService.getStatus(resource.id)
    } returns DELETING

    runBlocking { subject.cleanupEnvironment(environment) }

    verify(exactly = 0) {
      resourceHandler.delete(resource)
      resourceRepository.delete(resource.id)
    }
  }

  @Test
  fun `resource deletion is skipped if it's currently actuating`() {
    val resource = environment.resourcesSortedByDependencies.first() as Resource<Nothing>
    val resourceHandler = resourceHandlers.supporting(resource.kind)

    every {
      resourceHandler.actuationInProgress(resource)
    } returns true

    runBlocking { subject.cleanupEnvironment(environment) }

    verify(exactly = 0) {
      resourceHandler.delete(resource)
      resourceRepository.delete(resource.id)
    }
  }

  @ParameterizedTest
  @EnumSource(TaskStatus::class)
  fun `resource record is only deleted when all associated deletion tasks succeed`(status: TaskStatus) {
    val resource = environment.resourcesSortedByDependencies.first() as Resource<Nothing>
    val resourceHandler = resourceHandlers.supporting(resource.kind)
    val deletionTask = Task("${resource.id}:delete", "Delete resource ${resource.id}")

    every {
      resourceStatusService.getStatus(resource.id)
    } returns DELETING

    every {
      resourceRepository.eventHistory(any(), any())
    } returns listOf(ResourceDeletionLaunched(resource, "plugin", listOf(deletionTask)))

    every {
      resourceHandler.delete(resource)
    } returns listOf(deletionTask)

    every {
      taskLauncher.getTaskExecution("${resource.id}:delete")
    } returns object : TaskExecution {
      override val id: String = "${resource.id}:delete"
      override val name: String = "Delete resource ${resource.id}"
      override val application: String = "fnord"
      override val startTime: Instant? = null
      override val endTime: Instant? = null
      override val status: TaskStatus = status
    }

    runBlocking { subject.cleanupEnvironment(environment) }

    println("Checking deletion for status $status")
    verify(exactly = if (status == SUCCEEDED) 1 else 0) {
      resourceRepository.delete(resource.id)
    }
  }
}