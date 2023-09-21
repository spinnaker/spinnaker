package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.TaskForResource
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.time.MutableClock
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Test

internal class EnvironmentTaskCancelerTests {

  private val clock = MutableClock()
  private val application = "waffles"
  private val environment = "butter"
  private val artifactReference = "fork"
  private val user = "emily@emily"
  private val pin = EnvironmentArtifactPin(
    targetEnvironment = environment,
    reference = artifactReference,
    version = "v1",
    pinnedBy = user,
    comment = "this is a good version"
  )

  private val veto = EnvironmentArtifactVeto(
    targetEnvironment = environment,
    reference = artifactReference,
    version = "v0",
    vetoedBy = user,
    comment = "this is a very bad version"
  )

  private val taskTrackingRepository: TaskTrackingRepository = mockk() {
    every { getInFlightTasks(application, environment) } returns setOf(
      TaskForResource(
        id = "task1",
        name = "upsert vetoed version",
        resourceId = "1",
        startedAt = clock.instant(),
        endedAt = null,
        artifactVersion = "v0",
      ),
      TaskForResource(
        id = "task2",
        name = "upsert pinned version",
        resourceId = "1",
        startedAt = clock.instant(),
        endedAt = null,
        artifactVersion = "v1",
      )
    )
  }
  private val keelRepository: KeelRepository = mockk()
  private val taskLauncher: TaskLauncher = mockk(relaxUnitFun = true)

  //this is a spyk so that i can mock the relevant resource ids response instead of constructing a delivery config
  private val subject = spyk(EnvironmentTaskCanceler(taskTrackingRepository, keelRepository, taskLauncher))

  @Test
  fun `pin - cancels no tasks when there are no relevant resources`() {
    every { subject.getRelevantResourceIds(application, environment, artifactReference) } returns emptyList()

    subject.cancelTasksForPin(application, pin, user)

    coVerify(exactly = 0) { taskLauncher.cancelTasks(any(), user) }
  }

  @Test
  fun `veto - cancels no tasks when there are no relevant resources`() {
    every { subject.getRelevantResourceIds(application, environment, artifactReference) } returns emptyList()

    subject.cancelTasksForVeto(application, veto, user)

    coVerify(exactly = 0) { taskLauncher.cancelTasks(any(), user) }
  }

  @Test
  fun `pin - cancels running task for other version`() {
    every { subject.getRelevantResourceIds(application, environment, artifactReference) } returns listOf("1")

    subject.cancelTasksForPin(application, pin, user)

    coVerify(exactly = 1) { taskLauncher.cancelTasks(listOf("task1"), user) }
  }

  @Test
  fun `veto - cancels running task for vetoed version`() {
    every { subject.getRelevantResourceIds(application, environment, artifactReference) } returns listOf("1")

    subject.cancelTasksForVeto(application, veto, user)

    coVerify(exactly = 1) { taskLauncher.cancelTasks(listOf("task1"), user) }
  }

}
