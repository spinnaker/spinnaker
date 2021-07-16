package com.netflix.spinnaker.keel.titus.postdeploy

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.core.api.TagAmiPostDeployAction
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.test.DummyArtifact
import com.netflix.spinnaker.keel.titus.verification.TASKS
import com.netflix.spinnaker.keel.verification.ImageFinder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Instant.now

internal class TagAmiHandlerTests {
  private val orca : OrcaService = mockk()
  private val launcher : TaskLauncher = mockk()
  private val finder : ImageFinder = mockk()

  @Test
  fun getSupportedType() {
    expectThat(handler.supportedType.name).isEqualTo("tag-ami")
  }

  @Test
  fun `running the action launches a task`() {

    every { finder.getImages(deliveryConfig, "testing") } returns listOf(
      mockk {
        every { kind } returns mockk {
          every { group } returns "ec2"
        }
        every { images } returns emptyList()
      }
    )

    coEvery {
        launcher.submitJob(
          user=any(),
          application=any(),
          notifications = any(),
          subject=any(),
          description=any(),
          correlationId=any(),
          stages=any())
    } returns mockk() {
      every { id } returns "01FAP29KG410CVJHKXTEW5CA20"
    }

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
  fun `task succeeded`() {
    val taskId = "01FAP9T72MWQEF49QY2JNJHPWA"
    val oldState = ActionState(ConstraintStatus.PENDING, now(), null, metadata = mapOf(TASKS to listOf(taskId)))
    coEvery { orca.getOrchestrationExecution(taskId, any() ) } returns mockk() {
      every { id } returns taskId
      every { application } returns "fnord"
      every { status } returns TaskStatus.SUCCEEDED
    }

    val newState = runBlocking {
      handler.evaluate(context, mockk(), oldState)
    }

    expectThat(newState.status).isEqualTo(ConstraintStatus.PASS)
  }

  @Test
  fun `task terminal`() {
    val taskId = "01FAP9T72MWQEF49QY2JNJHPWA"
    val oldState = ActionState(ConstraintStatus.PENDING, now(), null, metadata = mapOf(TASKS to listOf(taskId)))
    coEvery { orca.getOrchestrationExecution(taskId, any() ) } returns mockk() {
      every { id } returns taskId
      every { application } returns "fnord"
      every { status } returns TaskStatus.TERMINAL
    }

    val newState = runBlocking {
      handler.evaluate(context, mockk(), oldState)
    }

    expectThat(newState.status).isEqualTo(ConstraintStatus.FAIL)
  }

  private val handler = TagAmiHandler(
    eventPublisher = mockk(),
    taskLauncher = launcher,
    orca = orca,
    spectator = NoopRegistry(),
    baseUrlConfig = mockk() { every { baseUrl } returns "https://spin" },
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
}
