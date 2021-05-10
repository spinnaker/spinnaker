package com.netflix.spinnaker.keel.preview

import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.branchName
import com.netflix.spinnaker.keel.api.artifacts.branchStartsWith
import com.netflix.spinnaker.keel.api.scm.CommitCreatedEvent
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.DataSources
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.DummyArtifactReferenceResourceSpec
import com.netflix.spinnaker.keel.test.DummyLocatableResourceSpec
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.artifactReferenceResource
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.locatableResource
import com.netflix.spinnaker.keel.test.submittedResource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.called
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

class PreviewEnvironmentCodeEventListenerTests : JUnit5Minutests {
  class Fixture {
    private val objectMapper = configuredTestObjectMapper()

    val repository: KeelRepository = mockk()

    val importer: DeliveryConfigImporter = mockk()

    val front50Cache: Front50Cache = mockk()

    val subject = PreviewEnvironmentCodeEventListener(repository, importer, front50Cache, objectMapper)

    val appConfig = Application(
      name = "fnord",
      email = "keel@keel.io",
      repoType = "stash",
      repoProjectKey = "myorg",
      repoSlug = "myrepo",
      dataSources = DataSources(enabled = emptyList(), disabled = emptyList())
    )

    val artifactFromMain = DockerArtifact(
      name = "myorg/myartifact",
      deliveryConfigName = "myconfig",
      reference = "myartifact-main",
      from = ArtifactOriginFilter(branch = branchName("main"))
    )

    val artifactFromBranch = artifactFromMain.copy(
      reference = "myartifact-branch",
      from = ArtifactOriginFilter(branch = branchStartsWith("feature/"))
    )

    val artifactWithNonMatchingFilter = artifactFromBranch.copy(
      reference = "myartifact-non-matching",
      from = ArtifactOriginFilter(branch = branchName("not-the-right-branch"))
    )

    var deliveryConfig = DeliveryConfig(
      application = "fnord",
      name = "myconfig",
      serviceAccount = "keel@keel.io",
      artifacts = setOf(artifactFromMain, artifactFromBranch),
      environments = setOf(
        Environment(
          name = "test",
          resources = setOf(
            locatableResource(),
            artifactReferenceResource(artifactReference = artifactFromMain.reference, artifactType = DOCKER)
          )
        )
      ),
      previewEnvironments = setOf(
        PreviewEnvironmentSpec(
          branch = branchStartsWith("feature/"),
          baseEnvironment = "test"
        )
      )
    )

    val commitEvent = CommitCreatedEvent(
      repoKey = "stash/myorg/myrepo",
      targetBranch = "feature/abc",
      commitHash = "1d52038730f431be19a8012f6f3f333e95a53772"
    )

    val nonMatchingCommitEvent = commitEvent.copy(targetBranch = "not-a-match")

    val previewEnv = slot<Environment>()

    fun setupMocks() {
      every {
        repository.allDeliveryConfigs(any())
      } returns setOf(deliveryConfig)

      every {
        front50Cache.applicationByName("fnord")
      } returns appConfig

      every {
        importer.import(commitEvent, "spinnaker.yml")
      } returns with(deliveryConfig) {
        SubmittedDeliveryConfig(
          application = application,
          name = name,
          serviceAccount = serviceAccount,
          metadata = metadata,
          artifacts = artifacts,
          previewEnvironments = previewEnvironments,
          environments = environments.map { env ->
            SubmittedEnvironment(
              name = env.name,
              resources = env.resources.map { res ->
                submittedResource(res.kind, res.spec)
              }.toSet()
            )
          }.toSet()
        )
      }

      every { repository.upsertResource<DummyResourceSpec>(any(), any()) } just runs

      every { repository.storeEnvironment(any(), capture(previewEnv)) } just runs
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("a delivery config exists in a branch") {
      before {
        setupMocks()
      }

      context("a commit event matching a preview environment spec is received") {
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

        test("a preview environment and associated resources are created/updated") {
          verify {
            repository.upsertResource(previewEnv.captured.resources.first(), deliveryConfig.name)
            repository.storeEnvironment(deliveryConfig.name, previewEnv.captured)
          }
        }

        test("the name of the preview environment is generated correctly") {
          val baseEnv = deliveryConfig.environments.first()
          val branchDetail = commitEvent.targetBranch.replace("/", "-")

          expectThat(previewEnv.captured) {
            get { name }.isEqualTo("${baseEnv.name}-$branchDetail")
          }
        }

        test("the name of monikered resources is generated correctly") {
          val branchDetail = commitEvent.targetBranch.replace("/", "-")
          val baseEnv = deliveryConfig.environments.first()
          val baseResource = baseEnv.resources.first() as Resource<Monikered>

          expectThat(previewEnv.captured.resources.first().spec)
            .isA<Monikered>()
            .get { moniker.detail }.isEqualTo("${baseResource.spec.moniker.detail}-$branchDetail")
        }

        test("the artifact reference in a resource is updated to match the preview environment branch filter") {
          expectThat(previewEnv.captured.resources.find { it.spec is ArtifactReferenceProvider }?.spec)
            .isA<ArtifactReferenceProvider>()
            .get { artifactReference }.isEqualTo(artifactFromBranch.reference)
        }
      }

      context("without an artifact in the delivery config matching the branch filter") {
        modifyFixture {
          deliveryConfig = deliveryConfig.copy(
            artifacts = setOf(artifactFromMain, artifactWithNonMatchingFilter)
          )
        }

        before {
          setupMocks() // to pick up the updated delivery config above
          subject.handleCommitCreated(commitEvent)
        }

        test("the artifact reference in a resource is not updated") {
          expectThat(previewEnv.captured.resources.find { it.spec is ArtifactReferenceProvider }?.spec)
            .isA<ArtifactReferenceProvider>()
            .get { artifactReference }.isEqualTo(artifactFromMain.reference)
        }
      }

      context("a commit event NOT matching a preview environment spec is received") {
        before {
          subject.handleCommitCreated(nonMatchingCommitEvent)
        }

        test("event is ignored") {
          verify(exactly = 0) {
            repository.upsertResource<DummyLocatableResourceSpec>(any(), deliveryConfig.name)
            repository.upsertResource<DummyArtifactReferenceResourceSpec>(any(), deliveryConfig.name)
            repository.storeEnvironment(deliveryConfig.name, any())
          }
          verify {
            importer wasNot called
          }
        }
      }
    }

    context("a commit event matching a preview spec branch filter but from a different app's repo") {
      before {
        setupMocks()

        every {
          front50Cache.applicationByName("fnord")
        } returns appConfig.copy(
          repoProjectKey = "anotherorg",
          repoSlug = "another-repo"
        )

        subject.handleCommitCreated(commitEvent)
      }

      test("event is ignored") {
        verify(exactly = 0) {
          repository.upsertResource<DummyLocatableResourceSpec>(any(), deliveryConfig.name)
          repository.upsertResource<DummyArtifactReferenceResourceSpec>(any(), deliveryConfig.name)
          repository.storeEnvironment(deliveryConfig.name, any())
        }
        verify {
          importer wasNot called
        }
      }
    }
  }
}