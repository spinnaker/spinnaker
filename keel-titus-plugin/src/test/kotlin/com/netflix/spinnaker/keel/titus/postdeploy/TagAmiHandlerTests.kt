package com.netflix.spinnaker.keel.titus.postdeploy

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.core.api.TagAmiPostDeployAction
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.test.DummyArtifact
import com.netflix.spinnaker.keel.verification.ImageFinder
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class TagAmiHandlerTests {

  private val orca : OrcaService = mockk()
  private val launcher : TaskLauncher = mockk()
  private val repo : ActionRepository = mockk()
  private val finder : ImageFinder = mockk()

  private val handler = TagAmiHandler(
    eventPublisher = mockk(),
    mapper = mockk(),
    taskLauncher = launcher,
    orca = orca,
    actionRepository = repo,
    spectator = NoopRegistry(),
    baseUrlConfig = mockk(),
    imageFinder = finder
  )
  private val deliveryConfig = DeliveryConfig(
    application = "fnord",
    name = "fnord",
    serviceAccount = "fnord@netflix.com",
    artifacts = setOf(DummyArtifact()),
    environments = setOf(Environment("testing"))
  )

  private val context = ArtifactInEnvironmentContext(
    deliveryConfig = deliveryConfig,
    environmentName = "testing",
    artifactReference = "fnord",
    version = "1.2.3"
  )

  @Test
  fun getSupportedType() {
    expectThat(handler.supportedType.name).isEqualTo("tag-ami")
  }

  @Test
  fun `running the action launches a task`() {
    every { repo.getStates(context, ActionType.VERIFICATION) } returns emptyMap()
    every { finder.getImages(deliveryConfig, "testing") } returns emptyList()

    runBlocking {
      handler.start(context, TagAmiPostDeployAction())
    }

    coVerify {
      launcher.submitJob(
        user=any(),
        application=any(),
        notifications = any(),
        subject=any(),
        description=any(),
        correlationId=any(),
        stages=any())
    }

  }

  @Test
  fun evaluate() {
  }

  @Test
  fun toJob() {
  }

  @Test
  fun getEventPublisher() {
  }
}
