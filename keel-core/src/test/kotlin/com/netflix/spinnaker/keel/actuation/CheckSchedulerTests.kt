package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
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
  private val artifactHandler = mockk<ArtifactHandler>(relaxUnitFun = true)
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

  private val artifacts = listOf(
    DebianArtifact(
      name = "fnord",
      vmOptions = VirtualMachineOptions(
        baseOs = "bionic-classic",
        regions = setOf("us-west-2", "us-east-1")
      )
    ),
    DockerArtifact(
      name = "fnord-but-like-in-a-container"
    )
  )

  fun tests() = rootContext<CheckScheduler> {
    fixture {
      CheckScheduler(
        repository = repository,
        resourceActuator = resourceActuator,
        environmentPromotionChecker = environmentPromotionChecker,
        artifactHandlers = listOf(artifactHandler),
        resourceCheckMinAgeDuration = Duration.ofMinutes(5),
        resourceCheckBatchSize = 2,
        checkTimeout = Duration.ofMinutes(2),
        publisher = publisher,
        agentLockRepository = agentLockRepository
      )
    }

    context("scheduler is disabled") {
      before {
        checkResources()
      }

      test("no resources are checked") {
        verify { resourceActuator wasNot Called }
      }

      test("no environments are checked") {
        verify { environmentPromotionChecker wasNot Called }
      }

      test("no artifacts are checked") {
        verify { artifactHandler wasNot Called }
      }
    }

    context("scheduler is enabled") {
      before {
        onApplicationUp()
      }

      after {
        onApplicationDown()
      }

      context("checking resources") {
        before {
          every {
            repository.resourcesDueForCheck(any(), any())
          } returns resources

          checkResources()
        }
        test("all resources are checked") {
          resources.forEach { resource ->
            coVerify(timeout = 500) {
              resourceActuator.checkResource(resource)
            }
          }
        }
      }

      context("checking artifacts") {
        before {
          every {
            repository.artifactsDueForCheck(any(), any())
          } returns artifacts

          checkArtifacts()
        }
        test("all artifacts are checked") {
          artifacts.forEach { artifact ->
            coVerify(timeout = 500) {
              artifactHandler.handle(artifact)
            }
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
