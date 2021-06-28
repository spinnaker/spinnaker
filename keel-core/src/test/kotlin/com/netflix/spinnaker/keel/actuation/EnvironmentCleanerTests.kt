package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.artifacts.branchStartsWith
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.locatableResource
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment as SpringEnvironment
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

class EnvironmentCleanerTests {
  private val deliveryConfigRepository: DeliveryConfigRepository = mockk()
  private val springEnv: SpringEnvironment = mockk()
  private val subject = EnvironmentCleaner(deliveryConfigRepository, springEnv)

  private val deliveryConfig = deliveryConfig(locatableResource()).copy(
    previewEnvironments = setOf(
      PreviewEnvironmentSpec(
        branch = branchStartsWith("feature/"),
        baseEnvironment = "test"
      )
    )
  )

  private val previewResource1 = locatableResource()
  private val previewResource2 = locatableResource()
  private val environment = Environment(
    name = "test-feature-abc",
    resources = setOf(previewResource1, previewResource2),
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
      deliveryConfigRepository.deleteEnvironment(deliveryConfig.name, environment.name)
    } just runs
  }

  @Test
  fun `the preview environment is not deleted when feature flag is off`() {
    every {
      springEnv.getProperty("keel.environment-deletion.enabled", Boolean::class.java, any())
    } returns false

    subject.cleanupEnvironment(environment)

    verify(exactly=0) {
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
}