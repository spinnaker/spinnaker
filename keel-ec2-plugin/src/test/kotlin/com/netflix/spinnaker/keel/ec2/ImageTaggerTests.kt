package com.netflix.spinnaker.keel.ec2

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.plugins.CurrentImages
import com.netflix.spinnaker.keel.api.plugins.ImageInRegion
import com.netflix.spinnaker.keel.telemetry.VerificationCompleted
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.core.env.Environment
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class ImageTaggerTests : JUnit5Minutests {
  object Fixture {
    private val mapper = configuredTestObjectMapper()
    val taskLauncher: TaskLauncher = mockk() {
      coEvery { submitJob(user = any(), application = "waffles", notifications = emptySet(), subject = any(), description = any(), correlationId = any(), stages = any())
      } returns Task("123", "blah")
    }
    private val springEnv: Environment = mockk {
      every { getProperty("keel.image.tagging.enabled", Boolean::class.java, any()) } returns true
    }
    val spectator: Registry = NoopRegistry()
    val tagger: ImageTagger = ImageTagger(mapper, taskLauncher, springEnv, spectator)

    val ec2images = listOf(CurrentImages(
      ResourceKind.parseKind("ec2/cluster@v1.1"),
      listOf(ImageInRegion("us-east-1", "my-waffles-are-great", "kitchen")),
      "my-resource"
    ))
    val titusImages = listOf(CurrentImages(
      ResourceKind.parseKind("titus/cluster@v1.1"),
      listOf(ImageInRegion("us-east-1", "my-waffles-are-great", "kitchen")),
      "my-resource"
    ))

    val eventWithImages = VerificationCompleted(
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
    val eventWithoutImages = eventWithImages.copy(metadata = emptyMap())
    val failedEvent = eventWithImages.copy(status = FAIL)
    val notEc2Event = eventWithoutImages.copy(
      metadata = mapOf(
        "taste" to "excellent",
        "task" to "eater=emily",
        "images" to titusImages
      )
    )
    val malformedImagesEvent = eventWithImages.copy(metadata = mapOf("images" to "pictures"))
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    context("ignored events") {
      test("failed verification") {
        tagger.onVerificationCompleted(failedEvent)
        verify { taskLauncher wasNot Called}
      }

      test("not ec2 cluster") {
        tagger.onVerificationCompleted(notEc2Event)
        verify { taskLauncher wasNot Called}
      }

      test("no images") {
        tagger.onVerificationCompleted(eventWithoutImages)
        verify { taskLauncher wasNot Called}
      }

      test("malformed images") {
        tagger.onVerificationCompleted(malformedImagesEvent)
        verify { taskLauncher wasNot Called}
      }
    }

    context("ec2 events") {
      test("task launched to tag") {
        tagger.onVerificationCompleted(eventWithImages)
        val jobSlot = slot<List<Map<String,Any?>>>()
        coVerify(exactly = 1) {
          taskLauncher.submitJob(
            user = any(),
            application = "waffles",
            notifications = emptySet(),
            subject = any(),
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
              mapOf("latest tested" to true, "breakfast" to "environment:passed", "my/docker:tag" to "passed")
            )
          }
        }
      }
    }
  }
}
