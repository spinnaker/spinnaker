package com.netflix.spinnaker.keel.actuation

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.EnvironmentDeletionConfig
import com.netflix.spinnaker.config.EnvironmentVerificationConfig
import com.netflix.spinnaker.config.PostDeployActionsConfig
import com.netflix.spinnaker.config.ResourceCheckConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.plugins.UnsupportedKind
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.persistence.AgentLockRepository
import com.netflix.spinnaker.keel.persistence.EnvironmentDeletionRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.postdeploy.PostDeployActionRunner
import com.netflix.spinnaker.keel.scheduled.ScheduledAgent
import com.netflix.spinnaker.keel.telemetry.ResourceLoadFailed
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.verification.VerificationRunner
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment as SpringEnvironment
import java.time.Duration

internal object CheckSchedulerTests : JUnit5Minutests {

  private val repository: KeelRepository = mockk()
  private val postDeployActionRunner: PostDeployActionRunner = mockk()
  private val resourceActuator = mockk<ResourceActuator>(relaxUnitFun = true)
  private val environmentPromotionChecker = mockk<EnvironmentPromotionChecker>()
  private val artifactHandler = mockk<ArtifactHandler>(relaxUnitFun = true)
  private val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
  private val registry = NoopRegistry()
  private val checkMinAge = Duration.ofMinutes(5)
  private val resourceCheckConfig = ResourceCheckConfig().also {
    it.minAgeDuration = checkMinAge
    it.batchSize = 2
  }
  private val verificationConfig = EnvironmentVerificationConfig().also {
    it.minAgeDuration = checkMinAge
    it.batchSize = 2
    it.timeoutDuration = Duration.ofMinutes(2)
  }
  private val postDeployConfig = PostDeployActionsConfig().also {
    it.minAgeDuration = checkMinAge
    it.batchSize = 2
  }

  private val springEnv: SpringEnvironment = mockk(relaxed = true) {
    every {
      getProperty("keel.check.min-age-duration", Duration::class.java, any())
    } returns checkMinAge
  }


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

  private val verificationRunner = mockk<VerificationRunner>()

  private val environmentDeletionRepository: EnvironmentDeletionRepository = mockk()

  private val environmentCleaner: EnvironmentCleaner = mockk()

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
      name = "fnord-but-like-in-a-container",
      branch = "main"
    )
  )

  private val environmentsForDeletion = listOf(
    Environment("my-preview-environment1"),
    Environment("my-preview-environment2")
  )

  fun tests() = rootContext<CheckScheduler> {
    fixture {
      CheckScheduler(
        repository = repository,
        environmentDeletionRepository = environmentDeletionRepository,
        resourceActuator = resourceActuator,
        environmentPromotionChecker = environmentPromotionChecker,
        postDeployActionRunner = postDeployActionRunner,
        artifactHandlers = listOf(artifactHandler),
        resourceCheckConfig = resourceCheckConfig,
        verificationConfig = verificationConfig,
        postDeployConfig = postDeployConfig,
        environmentDeletionConfig = EnvironmentDeletionConfig(),
        environmentCleaner = environmentCleaner,
        publisher = publisher,
        agentLockRepository = agentLockRepository,
        verificationRunner = verificationRunner,
        clock = MutableClock(),
        springEnv = springEnv,
        spectator = registry
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

      test("no environments are checked for deletion") {
        verify { environmentCleaner wasNot Called }
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
        context("resources are read from the database") {
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

        context("resources cannot be loaded from the database") {
          before {
            every {
              repository.resourcesDueForCheck(any(), any())
            } throws UnsupportedKind("some-invalid-kind")

            checkResources()
          }

          test("an event is published") {
            verify { publisher.publishEvent(match<Any> { it is ResourceLoadFailed }) }
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

      context("checking environments for deletion") {
        before {
          every {
            environmentDeletionRepository.itemsDueForCheck(any(), any())
          } returns environmentsForDeletion

          every {
            environmentCleaner.cleanupEnvironment(any())
          } just runs

          checkEnvironmentsForDeletion()
        }

        test("all environments marked for deletion are checked for cleanup") {
          environmentsForDeletion.forEach { environment ->
            coVerify(timeout = 500) {
              environmentCleaner.cleanupEnvironment(environment)
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
