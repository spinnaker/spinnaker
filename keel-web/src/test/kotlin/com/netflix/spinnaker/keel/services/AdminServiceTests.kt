package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.actuation.ExecutionSummaryService
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.PipelineConstraint
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.TimeWindow
import com.netflix.spinnaker.keel.core.api.TimeWindowConstraint
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
import com.netflix.spinnaker.keel.front50.model.Pipeline
import com.netflix.spinnaker.keel.front50.model.Stage
import com.netflix.spinnaker.keel.front50.model.Trigger
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.DummyArtifact
import com.netflix.spinnaker.keel.test.DummySortingStrategy
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isTrue
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

class AdminServiceTests : JUnit5Minutests {
  class Fixture {
    val repository: KeelRepository = mockk(relaxed = true)
    val diffFingerprintRepository: DiffFingerprintRepository = mockk()
    val actuationPauser: ActuationPauser = mockk()
    private val artifactSupplier = mockk<ArtifactSupplier<DummyArtifact, DummySortingStrategy>>(relaxUnitFun = true)
    val front50Cache: Front50Cache = mockk()
    val front50Service: Front50Service = mockk()
    val publisher: ApplicationEventPublisher = mockk(relaxed = true)
    val clock = MutableClock()

    val application = "leapp"

    val environment = Environment(
      name = "test",
      constraints = setOf(
        ManualJudgementConstraint(),
        PipelineConstraint(pipelineId = "wowapipeline"),
        TimeWindowConstraint(windows = listOf(TimeWindow(days = "Monday")))
      )
    )

    val artifact = mockk<DeliveryArtifact> {
      every { reference } returns "myartifact"
    }

    val deliveryConfig = DeliveryConfig(
      name = "manifest",
      application = application,
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(environment)
    )

    val front50Application = Application(
      name = deliveryConfig.name,
      email = "keel@keel.io",
      repoType = "stash",
      repoProjectKey = "proj",
      repoSlug = "repo",
    )

    val importPipeline = Pipeline(
      name = "Import",
      id = "config",
      application = front50Application.name,
      disabled = false,
      triggers = listOf(Trigger(type = "trigger", enabled = true, application = front50Application.name)),
      _stages = listOf(Stage(type = "importDeliveryConfig", name = "Import config", refId = "1"))
    )

    val executionSummaryService: ExecutionSummaryService = mockk()

    val subject = AdminService(
      repository,
      actuationPauser,
      diffFingerprintRepository,
      listOf(artifactSupplier),
      front50Cache,
      front50Service,
      executionSummaryService,
      publisher,
      clock
    )

    fun verifyMigrationWasSkipped() {
      runBlocking {
        subject.migrateImportPipelinesToGitIntegration()
      }
      verify(exactly = 0) {
        front50Service.updateApplication(front50Application.name, any(), any())
      }
      verify(exactly = 0) {
        front50Service.updatePipeline(importPipeline.id, any(), any())
      }
    }
  }

  fun adminServiceTests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    before {
      every { repository.getDeliveryConfigForApplication(application) } returns deliveryConfig
    }

    context("forcing environment constraint reevaluation") {
      test("clears state only for stateful constraints") {
        subject.forceConstraintReevaluation(application, environment.name)

        verify(exactly = 1) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "manual-judgement") }
        verify(exactly = 1) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "pipeline") }
        verify(exactly = 1) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "allowed-times") }
      }

      test("clears a specific constraint type when asked to") {
        subject.forceConstraintReevaluation(application, environment.name, "pipeline")

        verify(exactly = 0) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "manual-judgement") }
        verify(exactly = 1) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "pipeline") }
        verify(exactly = 0) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "allowed-times") }
      }
    }

    test("forcing an artifact version to be skipped") {
      val current = mockk<PublishedArtifact> {
        every { reference } returns artifact.reference
        every { version } returns "v16"
      }

      every { repository.getArtifactVersionsByStatus(deliveryConfig, environment.name, listOf(CURRENT)) } returns listOf(current)

      subject.forceSkipArtifactVersion(application, environment.name, artifact.reference, "v15")

      verify(exactly = 1) { repository.markAsSkipped(deliveryConfig, artifact, "v15", environment.name, "v16") }
    }

    context("refreshing the application cache") {
      before {
        every { front50Cache.primeCaches() } just runs
        subject.refreshApplicationCache()
      }

      test("delegates to the cache") {
        verify { front50Cache.primeCaches() }
      }
    }

    context("git integration migration") {
      before {
        every { repository.allDeliveryConfigs() } returns setOf(deliveryConfig)
        every { front50Cache.applicationByName(deliveryConfig.application) } returns front50Application
        every { front50Cache.pipelinesByApplication(front50Application.name) } returns listOf(importPipeline)
        every { front50Service.updateApplication(front50Application.name, any(), any()) } returns front50Application
        every { front50Service.updatePipeline(importPipeline.id, any(), any()) } just Runs
      }

      test("migrated successfully") {
        val updatedApp = slot<Application>()
        val updatedPipeline = slot<Pipeline>()
        runBlocking {
          subject.migrateImportPipelinesToGitIntegration()
        }
        verify {
          front50Service.updateApplication(front50Application.name, any(), capture(updatedApp))
        }
        verify {
          front50Service.updatePipeline(importPipeline.id, capture(updatedPipeline), any())
        }
        expectThat(updatedApp.captured.managedDelivery?.importDeliveryConfig).isTrue()
        expectThat(updatedPipeline.captured.disabled).isTrue()
      }

      context("git integration already enabled") {
        before {
          every { front50Cache.applicationByName(deliveryConfig.application) } returns front50Application.copy(
            managedDelivery = ManagedDeliveryConfig(
              importDeliveryConfig = true
            )
          )
        }
        test("skipping migration") {
          runBlocking {
            subject.migrateImportPipelinesToGitIntegration()
          }
          verifyMigrationWasSkipped()
        }
      }

      context("no import config pipeline") {
        before {
          every { front50Cache.pipelinesByApplication(front50Application.name) } returns listOf(
            importPipeline.copy(
              _stages = listOf(Stage(type = "coolStage", name = "cool", refId = "1"))
            )
          )
        }
        test("skipping migration") {
          verifyMigrationWasSkipped()
        }
      }

      context("pipeline is disabled") {
        before {
          every { front50Cache.pipelinesByApplication(front50Application.name) } returns listOf(
            importPipeline.copy(
              disabled = true
            )
          )
        }
        test("skipping migration") {
          verifyMigrationWasSkipped()
        }
      }

      context("pipeline triggers are disabled") {
        before {
          every { front50Cache.pipelinesByApplication(front50Application.name) } returns listOf(
            importPipeline.copy(
              triggers = listOf(Trigger(type = "trigger", enabled = false, application = front50Application.name))
            )
          )
        }
        test("skipping migration") {
          verifyMigrationWasSkipped()
        }
      }

      context("failure to update git integration") {
        before {
          every {
            front50Service.updateApplication(
              front50Application.name,
              any(),
              any()
            )
          } throws Exception("This is awful")
        }
        test("pipeline update is skipped") {
          runBlocking {
            subject.migrateImportPipelinesToGitIntegration()
          }
          verify(exactly = 1) {
            front50Service.updateApplication(front50Application.name, any(), any())
          }
          verify(exactly = 0) {
            front50Service.updatePipeline(importPipeline.id, any(), any())
          }
        }
      }
    }
  }
}
