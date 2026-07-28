package com.netflix.spinnaker.keel.ec2

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.plugins.CurrentImages
import com.netflix.spinnaker.keel.api.plugins.ImageInRegion
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests.DummyVerification
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.VerificationCompleted
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.deliveryConfig
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import org.springframework.core.env.Environment as SpringEnv

class ImageTaggerTests {
  private val mapper = configuredTestObjectMapper()
  private val taskLauncher: TaskLauncher = mockk() {
    coEvery {
      submitJob(
        user = any(),
        application = "waffles",
        environmentName = any(),
        resourceId = any(),
        notifications = emptySet(),
        description = any(),
        correlationId = any(),
        stages = any()
      )
    } returns Task("123", "blah")
  }
  private val springEnv: MockEnvironment = MockEnvironment().apply {
    setProperty("keel.image.tagging.enabled", "true")
  }

  private val artifact = DebianArtifact(
    reference = "waffle",
    name = "waffle",
    deliveryConfigName = "waffles",
    vmOptions = VirtualMachineOptions(baseOs = "butter-classic", regions = setOf("table", "plate"))
  )
  private val env = Environment(
    name = "breakfast",
    verifyWith = listOf(
      DummyVerification("bacon"),
      DummyVerification("eggs")
    )
  )
  private val config: DeliveryConfig = deliveryConfig(application = "waffles", configName = "waffles", env = env, artifact = artifact)

  private val spectator: Registry = NoopRegistry()
  private val keelRepository: KeelRepository = mockk() {
    every { getDeliveryConfigForApplication("waffles") } returns config
  }
  private val actionRepository: ActionRepository = mockk() {
    every { allPassed(any(), ActionType.VERIFICATION) } returns false
  }

  private val tagger: ImageTagger = ImageTagger(mapper, taskLauncher, actionRepository, keelRepository, springEnv, spectator)
  private val ec2images = listOf(CurrentImages(
    ResourceKind.parseKind("ec2/cluster@v1.1"),
    listOf(ImageInRegion("us-east-1", "my-waffles-are-great", "kitchen")),
    "my-resource"
  ))

  private val titusImages = listOf(CurrentImages(
    ResourceKind.parseKind("titus/cluster@v1.1"),
    listOf(ImageInRegion("us-east-1", "my-waffles-are-great", "kitchen")),
    "my-resource"
  ))

  private val eventWithImages = VerificationCompleted(
    application = "waffles",
    deliveryConfigName = "waffles-manifest",
    environmentName = "breakfast",
    artifactReference = "waffle",
    artifactType = DEBIAN,
    artifactVersion = "waffle-buttermilk-2.0",
    verificationType = "taste-test",
    verificationId = "my/docker:tag",
    status = PASS,
    metadata = mapOf(
      "taste" to "excellent",
      "task" to "eater=emily",
      "images" to ec2images
    )
  )
  private val eventWithoutImages = eventWithImages.copy(metadata = emptyMap())
  private val failedEvent = eventWithImages.copy(status = FAIL)
  private val notEc2Event = eventWithoutImages.copy(
    metadata = mapOf(
      "taste" to "excellent",
      "task" to "eater=emily",
      "images" to titusImages
    )
  )
  private val malformedImagesEvent = eventWithImages.copy(metadata = mapOf("images" to "pictures"))

  @Nested
  inner class IgnoredEvents {
    @Test
    fun `failed verification`() {
      tagger.onVerificationCompleted(failedEvent)
      verify { taskLauncher wasNot Called}
    }

    @Test
    fun `not ec2 cluster`() {
      tagger.onVerificationCompleted(notEc2Event)
      verify { taskLauncher wasNot Called}
    }

    @Test
    fun `no images`() {
      tagger.onVerificationCompleted(eventWithoutImages)
      verify { taskLauncher wasNot Called}
    }

    @Test
    fun `malformed images`() {
      tagger.onVerificationCompleted(malformedImagesEvent)
      verify { taskLauncher wasNot Called}
    }

    @Test
    fun `verifications not complete yet`() {
      tagger.onVerificationCompleted(eventWithImages)
      verify { taskLauncher wasNot Called}
    }
  }

  @Nested
  inner class Ec2Events {
    @BeforeEach
    fun setup() {
      every { actionRepository.allPassed(any(), ActionType.VERIFICATION) } returns true
    }

    @Test
    fun `task launched to tag`() {
      tagger.onVerificationCompleted(eventWithImages)
      val jobSlot = slot<List<Map<String,Any?>>>()
      coVerify(exactly = 1) {
        taskLauncher.submitJob(
          user = any(),
          application = "waffles",
          environmentName = any(),
          resourceId = any(),
          notifications = emptySet(),
          description = any(),
          correlationId = any(),
          stages = capture(jobSlot)
        )
      }
      expect {
        with(jobSlot.captured) {
          that(size).isEqualTo(1)
          that(first()["type"]).isEqualTo("upsertImageTags")
          that(first()["tags"]).isA<Map<String,Any?>>().isEqualTo(
            mapOf("latest tested" to true, "breakfast" to "environment:passed")
          )
        }
      }
    }
  }
}
