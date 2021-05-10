package com.netflix.spinnaker.keel.scm

import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.branchName
import com.netflix.spinnaker.keel.api.scm.CommitCreatedEvent
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.DataSources
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.submittedResource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.called
import io.mockk.coEvery as every
import io.mockk.mockk
import io.mockk.coVerify as verify

class DeliveryConfigImportListenerTests : JUnit5Minutests {
  class Fixture {
    val repository: KeelRepository = mockk()

    val importer: DeliveryConfigImporter = mockk()

    val front50Cache: Front50Cache = mockk()

    val subject = DeliveryConfigImportListener(repository, importer, front50Cache)

    val configuredApp = Application(
      name = "fnord",
      email = "keel@keel.io",
      repoType = "stash",
      repoProjectKey = "myorg",
      repoSlug = "myrepo",
      importDeliveryConfig = true,
      defaultBranch = "main",
      dataSources = DataSources(enabled = emptyList(), disabled = emptyList())
    )

    val notConfiguredApp = Application(
      name = "notfnord",
      email = "keel@keel.io",
      repoType = "stash",
      repoProjectKey = "myorg",
      repoSlug = "another-repo",
      dataSources = DataSources(enabled = emptyList(), disabled = emptyList())
    )

    val artifactFromMain = DockerArtifact(
      name = "myorg/myartifact",
      deliveryConfigName = "myconfig",
      reference = "myartifact-main",
      from = ArtifactOriginFilter(branch = branchName("main"))
    )

    val deliveryConfig = SubmittedDeliveryConfig(
      application = "fnord",
      name = "myconfig",
      serviceAccount = "keel@keel.io",
      artifacts = setOf(artifactFromMain),
      environments = setOf(
        SubmittedEnvironment(
          name = "test",
          resources = setOf(
            submittedResource()
          )
        )
      )
    )

    val commitEvent = CommitCreatedEvent(
      repoKey = "stash/myorg/myrepo",
      targetBranch = "main",
      commitHash = "1d52038730f431be19a8012f6f3f333e95a53772"
    )

    val commitEventForAnotherBranch = commitEvent.copy(targetBranch = "not-a-match")

    // matches repo for nonConfiguredApp
    val commitEventForAnotherRepo = commitEvent.copy(repoKey = "stash/myorg/another-repo")

    fun setupMocks() {
      every {
        front50Cache.allApplications()
      } returns listOf(configuredApp, notConfiguredApp)

      every {
        importer.import(commitEvent, any())
      } returns deliveryConfig

      every {
        repository.upsertDeliveryConfig(deliveryConfig)
      } returns deliveryConfig.toDeliveryConfig()
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("an application is configured to retrieve the delivery config from source") {
      before {
        setupMocks()
      }

      context("a commit event matching the repo and branch is received") {
        before {
          subject.handleCommitCreated(commitEvent)
        }

        test("the delivery config is imported from the commit in the event") {
          verify(exactly = 1) {
            importer.import(
              commitEvent = commitEvent,
              manifestPath = "spinnaker.yml"
            )
          }
        }

        test("the delivery config is created/updated") {
          verify {
            repository.upsertDeliveryConfig(deliveryConfig)
          }
        }
      }

      context("a commit event NOT matching the app repo is received") {
        before {
          subject.handleCommitCreated(commitEventForAnotherRepo)
        }

        test("the event is ignored") {
          verify {
            importer wasNot called
          }
          verify {
            repository wasNot called
          }
        }
      }

      context("a commit event NOT matching the app default branch is received") {
        before {
          subject.handleCommitCreated(commitEventForAnotherBranch)
        }

        test("the event is ignored") {
          verify {
            importer wasNot called
          }
          verify {
            repository wasNot called
          }
        }
      }
    }

    context("an application is NOT configured to retrieve the delivery config from source") {
      before {
        setupMocks()
      }

      context("a commit event matching the repo and branch is received") {
        before {
          subject.handleCommitCreated(commitEventForAnotherRepo)
        }

        test("the event is ignored") {
          verify {
            importer wasNot called
          }
          verify {
            repository wasNot called
          }
        }
      }
    }
  }
}