package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.titus.TestContainerVerification
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup.Location
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.titus.ContainerRunner
import de.huxhorn.sulky.ulid.ULID
import io.mockk.mockk
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import io.mockk.coVerify as verify

internal class TestContainerVerificationEvaluatorTests {

  private val containerRunner: ContainerRunner = mockk()
  private val subject = TestContainerVerificationEvaluator(
    containerRunner = containerRunner,
    linkStrategy = null
  )

  private val context = ArtifactInEnvironmentContext(
    deliveryConfig = deliveryConfig(),
    environmentName = "test",
    artifactReference = "fnord",
    version = "1.1"
  )

  private val app = "fnord-test-app"
  private val loc = Location(
    account = "titustestvpc",
    region = "ap-south-1"
  )

  private val verification = TestContainerVerification(
    repository = "illuminati/fnord",
    location = loc,
    application = app
  )

  @Test
  fun `starting verification launches a container job via containerRunner`() {
    val taskId = stubTaskLaunch()

    expectCatching { subject.start(context, verification) }
      .isSuccess()
      .get(TASKS)
      .isA<Iterable<String>>()
      .first() isEqualTo taskId

    verify {
      containerRunner.launchContainer(
        imageId = any(),
        subjectLine = any(),
        description = any(),
        serviceAccount = context.deliveryConfig.serviceAccount,
        application = any(),
        containerApplication = any(),
        environmentName = context.environmentName,
        location = verification.location,
        environmentVariables = any()
      )
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun verifyImageId(expectedImageId : String) {
    verify {
      containerRunner.launchContainer(
        imageId = match {
          it == expectedImageId
        },
        subjectLine = any(),
        description = any(),
        serviceAccount = any(),
        application = any(),
        containerApplication = any(),
        environmentName = any(),
        location = any(),
        environmentVariables = any()
      )
    }
  }

  @Test
  fun `image id specified by repository field and tag`() {
    stubTaskLaunch()

    subject.start(context, TestContainerVerification(repository="illuminati/fnord", tag="stable", location=loc, application=app))

    verifyImageId("illuminati/fnord:stable")
  }

  @Test
  fun `image id specified by repository field, no tag`() {
    stubTaskLaunch()

    subject.start(context, TestContainerVerification(repository="illuminati/fnord", location=loc, application=app))

    verifyImageId("illuminati/fnord:latest")
  }

  @Test
  fun `image id specified by image field and tag`() {
    stubTaskLaunch()

    subject.start(context, TestContainerVerification(image="acme/rollerskates:rocket", location=loc, application=app))

    verifyImageId("acme/rollerskates:rocket")
  }

  @Test
  fun `image id specified by image field, no tag`() {
    stubTaskLaunch()

    subject.start(context, TestContainerVerification(image="acme/rollerskates", location=loc, application=app))

    verifyImageId("acme/rollerskates:latest")
  }

  @Suppress("UNCHECKED_CAST")
  private fun verifyApplication(expectedApplication : String) {
    verify {
      containerRunner.launchContainer(
        imageId = any(),
        subjectLine = any(),
        description = any(),
        serviceAccount = any(),
        application = any(),
        containerApplication = match {
          it == expectedApplication
        },
        environmentName = any(),
        location = any(),
        environmentVariables = any()
      )
    }
  }

  @Test
  fun `container job runs with verification's application`() {
    stubTaskLaunch()
    subject.start(context, verification)
    verifyApplication(verification.application!!)
  }

  @Test
  fun `if no application is specified container job runs with delivery config's application`() {
    stubTaskLaunch()
    subject.start(context, verification.copy(application = null))
    verifyApplication(context.deliveryConfig.application)
  }



  private fun stubTaskLaunch(): String =
    ULID()
      .nextULID()
      .also { taskId ->
        io.mockk.coEvery {
          containerRunner.launchContainer(
            imageId = any(),
            subjectLine = any(),
            description = any(),
            serviceAccount = any(),
            application = any(),
            environmentName = any(),
            location = any(),
            environmentVariables = any(),
            containerApplication = any(),
          )
        } answers { mapOf(TASKS to listOf(taskId)) }
      }


}
