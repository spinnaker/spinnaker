package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.events.TaskCreatedEvent
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.TaskRecord
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import org.springframework.context.ApplicationEventPublisher

internal class OrcaTaskMonitorAgentTests : JUnit5Minutests {

  data class OrcaTaskMonitorAgentFixture(
    val event: TaskCreatedEvent,
    val repository: TaskTrackingRepository = mockk(relaxUnitFun = true),
    val resourceRepository: ResourceRepository = mockk(relaxUnitFun = true),
    val orcaService: OrcaService = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true),
    private val clock: Clock = Clock.systemDefaultZone()

  ) {
    val listener: OrcaTaskMonitorAgent = OrcaTaskMonitorAgent(repository, resourceRepository, orcaService, publisher, clock)
  }

  fun orcaTaskMonitorAgentTests() = rootContext<OrcaTaskMonitorAgentFixture> {
    fixture {
      OrcaTaskMonitorAgentFixture(
        event = TaskCreatedEvent(
          TaskRecord(
            id = "123",
            subject = "titus:cluster:titustestvpc:app-env13",
            name = "upsert server group")
        )
      )
    }

    context("a new task is being created in orca") {
      before {
          listener.onTaskEvent(event)
      }

      test("a new record is being added to the table exactly once") {
        verify(exactly = 1) { repository.store(any()) }
      }
    }
  }
}
