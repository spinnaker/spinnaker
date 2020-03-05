package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.persistence.AgentLockRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.scheduled.ScheduledAgent
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import org.springframework.context.ApplicationEventPublisher

internal object CheckSchedulerTests : JUnit5Minutests {

  private val repository: KeelRepository = mockk()
  private val resourceActuator = mockk<ResourceActuator>(relaxUnitFun = true)
  private val environmentPromotionChecker = mockk<EnvironmentPromotionChecker>()
  private val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)

  class DummyScheduledAgent(override val lockTimeoutSeconds: Long) : ScheduledAgent {
    override suspend fun invokeAgent() {
    }
  }

  private val dummyAgent = mockk<DummyScheduledAgent>(relaxUnitFun = true) {
    every {
      lockTimeoutSeconds
    } returns 5
  }

  private var agentLockRepository = mockk<AgentLockRepository>(relaxUnitFun = true) {
    every { agents } returns listOf(dummyAgent)
  }

  private val resources = listOf(
    resource(
      kind = parseKind("ec2/security-group@v1"),
      id = "ec2:security-group:prod:ap-south-1:keel-sg",
      application = "keel"
    ),
    resource(
      kind = parseKind("ec2/cluster@v1"),
      id = "ec2:cluster:prod:keel",
      application = "keel"
    )
  )

  fun tests() = rootContext<CheckScheduler> {
    fixture {
      CheckScheduler(
        repository = repository,
        resourceActuator = resourceActuator,
        environmentPromotionChecker = environmentPromotionChecker,
        resourceCheckMinAgeDuration = Duration.ofMinutes(5),
        resourceCheckBatchSize = 2,
        publisher = publisher,
        agentLockRepository = agentLockRepository
      )
    }

    context("scheduler is disabled") {
      test("nothing happens") {
        checkResources()

        verify { resourceActuator wasNot Called }
      }
    }

    context("scheduler is enabled") {
      before {
        onApplicationUp()

        every {
          repository.resourcesDueForCheck(any(), any())
        } returns resources
      }

      after {
        onApplicationDown()
      }

      test("all resources are checked") {
        checkResources()

        resources.forEach { resource ->
          coVerify(timeout = 500) {
            resourceActuator.checkResource(resource)
          }
        }
      }
    }

    context("test invoke agents") {
      before {
        onApplicationUp()
      }

      test("invoke a single agent") {
        every {
          agentLockRepository.tryAcquireLock(any(), any())
        } returns true

        invokeAgent()

        coVerify {
          dummyAgent.invokeAgent()
        }
      }
      after {
        onApplicationDown()
        clearAllMocks()
      }
    }
  }
}
