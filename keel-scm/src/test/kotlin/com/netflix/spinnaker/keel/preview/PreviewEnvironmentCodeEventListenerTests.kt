package com.netflix.spinnaker.keel.preview

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Dependency
import com.netflix.spinnaker.keel.api.DependencyType.GENERIC_RESOURCE
import com.netflix.spinnaker.keel.api.DependencyType.SECURITY_GROUP
import com.netflix.spinnaker.keel.api.Dependent
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.branchName
import com.netflix.spinnaker.keel.api.artifacts.branchStartsWith
import com.netflix.spinnaker.keel.api.ec2.EC2_SECURITY_GROUP_V1
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.core.api.TagAmiPostDeployAction
import com.netflix.spinnaker.keel.core.name
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.DataSources
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.notifications.DeliveryConfigImportFailed
import com.netflix.spinnaker.keel.persistence.ApproveOldVersionTests.DummyImplicitConstraint
import com.netflix.spinnaker.keel.notifications.DismissibleNotification
import com.netflix.spinnaker.keel.persistence.DismissibleNotificationRepository
import com.netflix.spinnaker.keel.persistence.EnvironmentDeletionRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.preview.PreviewEnvironmentCodeEventListener.Companion.APPLICATION_RETRIEVAL_ERROR
import com.netflix.spinnaker.keel.preview.PreviewEnvironmentCodeEventListener.Companion.CODE_EVENT_COUNTER
import com.netflix.spinnaker.keel.preview.PreviewEnvironmentCodeEventListener.Companion.COMMIT_HANDLING_DURATION
import com.netflix.spinnaker.keel.preview.PreviewEnvironmentCodeEventListener.Companion.DELIVERY_CONFIG_NOT_FOUND
import com.netflix.spinnaker.keel.preview.PreviewEnvironmentCodeEventListener.Companion.PREVIEW_ENVIRONMENT_MARK_FOR_DELETION_SUCCESS
import com.netflix.spinnaker.keel.preview.PreviewEnvironmentCodeEventListener.Companion.PREVIEW_ENVIRONMENT_UPSERT_ERROR
import com.netflix.spinnaker.keel.preview.PreviewEnvironmentCodeEventListener.Companion.PREVIEW_ENVIRONMENT_UPSERT_SUCCESS
import com.netflix.spinnaker.keel.scm.CommitCreatedEvent
import com.netflix.spinnaker.keel.scm.DELIVERY_CONFIG_RETRIEVAL_ERROR
import com.netflix.spinnaker.keel.scm.DELIVERY_CONFIG_RETRIEVAL_SUCCESS
import com.netflix.spinnaker.keel.scm.PrDeclinedEvent
import com.netflix.spinnaker.keel.scm.PrDeletedEvent
import com.netflix.spinnaker.keel.scm.PrMergedEvent
import com.netflix.spinnaker.keel.scm.PrOpenedEvent
import com.netflix.spinnaker.keel.scm.toTags
import com.netflix.spinnaker.keel.test.DummyArtifactReferenceResourceSpec
import com.netflix.spinnaker.keel.test.DummyDependentResourceSpec
import com.netflix.spinnaker.keel.test.DummyLocatableResourceSpec
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.artifactReferenceResource
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.dependentResource
import com.netflix.spinnaker.keel.test.locatableResource
import com.netflix.spinnaker.keel.test.submittedResource
import com.netflix.spinnaker.keel.validators.DeliveryConfigValidator
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.TestContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.called
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsKeys
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.one
import java.time.Clock
import java.time.Duration
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

class PreviewEnvironmentCodeEventListenerTests : JUnit5Minutests {
  class Fixture {
    private val objectMapper = configuredTestObjectMapper()
    private val clock: Clock = MutableClock()
    val fakeTimer: Timer = mockk()
    val repository: KeelRepository = mockk()
    val environmentDeletionRepository: EnvironmentDeletionRepository = mockk()
    val notificationRepository: DismissibleNotificationRepository = mockk()
    val importer: DeliveryConfigImporter = mockk()
    val front50Cache: Front50Cache = mockk()
    val springEnv: org.springframework.core.env.Environment = mockk()
    val spectator: Registry = mockk()
    val eventPublisher: ApplicationEventPublisher = mockk()
    val validator: DeliveryConfigValidator = mockk()
    val subject = spyk(PreviewEnvironmentCodeEventListener(
      repository = repository,
      environmentDeletionRepository = environmentDeletionRepository,
      notificationRepository = notificationRepository,
      deliveryConfigImporter = importer,
      deliveryConfigValidator = validator,
      front50Cache = front50Cache,
      objectMapper = objectMapper,
      springEnv = springEnv,
      spectator = spectator,
      clock = clock,
      eventPublisher = eventPublisher
    ))

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

    val locatableResource = locatableResource()

    val artifactReferenceResource = artifactReferenceResource(artifactReference = artifactFromMain.reference, artifactType = DOCKER)

    val defaultSecurityGroup = Resource(
      kind = EC2_SECURITY_GROUP_V1.kind,
      metadata = mapOf("id" to "fnord", "application" to "fnord"),
      spec = SecurityGroupSpec(
        moniker = Moniker("fnord"),
        locations = locatableResource.spec.locations,
        description = "default security group"
      )
    )

    val resourceWithSameNameAsDefaultSecurityGroup = locatableResource(moniker = Moniker("fnord"))

    val dependentResource = dependentResource(
      dependsOn = setOf(
        Dependency(
          type = GENERIC_RESOURCE,
          region = locatableResource.spec.locations.regions.first().name,
          name = locatableResource.name,
          kind = locatableResource.kind
        ),
        Dependency(
          type = SECURITY_GROUP,
          region = defaultSecurityGroup.spec.locations.regions.first().name,
          name = defaultSecurityGroup.name
        )
      )
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
            locatableResource,
            artifactReferenceResource,
            resourceWithSameNameAsDefaultSecurityGroup, // name conflict with default sec group, but different kind
            defaultSecurityGroup,
            dependentResource
          ),
          constraints = setOf(ManualJudgementConstraint()),
          postDeploy = listOf(TagAmiPostDeployAction())
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
      commitHash = "1d52038730f431be19a8012f6f3f333e95a53772",
      pullRequestId = "42"
    )

    val nonMatchingCommitEvent = commitEvent.copy(targetBranch = "not-a-match")

    val previewEnv = slot<Environment>()

    fun setupMocks() {
      every {
        springEnv.getProperty("keel.previewEnvironments.enabled", Boolean::class.java, true)
      } returns true


      every {
        spectator.counter(any(), any<Iterable<Tag>>())
      } returns mockk {
        every {
          increment()
        } just runs
      }

      every {
        validator.validate(any())
      } just runs

      every {
        spectator.timer(any(), any<Iterable<Tag>>())
      } returns fakeTimer

      every {
        fakeTimer.record(any<Duration>())
      } just runs

      every {
        eventPublisher.publishEvent(any<Object>())
      } just runs

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
              }.toSet(),
              constraints = env.constraints,
              postDeploy = env.postDeploy
            )
          }.toSet()
        )
      }

      every { repository.upsertResource<DummyResourceSpec>(any(), any()) } just runs

      every { repository.storeEnvironment(any(), capture(previewEnv)) } just runs

      every { environmentDeletionRepository.markForDeletion(any()) } just runs

      every {
        notificationRepository.dismissNotification(any<Class<DismissibleNotification>>(), any(), any())
      } returns true
    }
  }

  val prOpenedEvent = PrOpenedEvent(
    repoKey = "stash/myorg/myrepo",
    pullRequestBranch = "refs/heads/feature/abc",
    targetBranch = "main",
    pullRequestId = "42"
  )

  val prMergedEvent = PrMergedEvent(
    repoKey = "stash/myorg/myrepo",
    pullRequestBranch = "feature/abc",
    targetBranch = "main",
    pullRequestId = "42"
  )

  val prDeclinedEvent = PrDeclinedEvent(
    repoKey = "stash/myorg/myrepo",
    pullRequestBranch = "feature/abc",
    targetBranch = "main",
    pullRequestId = "42"
  )

  val prDeletedEvent = PrDeletedEvent(
    repoKey = "stash/myorg/myrepo",
    pullRequestBranch = "feature/abc",
    targetBranch = "main",
    pullRequestId = "42"
  )

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("a delivery config exists in a branch") {
      before {
        setupMocks()
      }

      context("a PR opened event matching a preview environment spec is received") {
        before {
          subject.handlePrOpened(prOpenedEvent)
        }

        test("processing is delegated to the commit created event handler") {
          verify(exactly = 1) {
            with(prOpenedEvent) {
              subject.handleCommitCreated(CommitCreatedEvent(repoKey, pullRequestBranch, pullRequestId, pullRequestBranch))
            }
          }
        }
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

        test("the notification was dimissed on successful import") {
          verify {
            notificationRepository.dismissNotification(any<Class<DismissibleNotification>>(), deliveryConfig.application, commitEvent.targetBranch, any())
          }
        }

        test("a successful delivery config retrieval is counted") {
          val tags = mutableListOf<Iterable<Tag>>()
          verify {
            spectator.counter(CODE_EVENT_COUNTER, capture(tags))
          }
          expectThat(tags).one {
            contains(DELIVERY_CONFIG_RETRIEVAL_SUCCESS.toTags())
          }
        }

        test("a successful preview environment upsert is counted") {
          val tags = mutableListOf<Iterable<Tag>>()
          verify {
            spectator.counter(CODE_EVENT_COUNTER, capture(tags))
          }
          expectThat(tags).one {
            contains(PREVIEW_ENVIRONMENT_UPSERT_SUCCESS.toTags())
          }
        }

        test("a duration is recorded for successful handling of the commit event") {
          verify(exactly = 1) {
            spectator.timer(COMMIT_HANDLING_DURATION, any<Iterable<Tag>>())
            fakeTimer.record(any<Duration>())
          }
        }

        test("a preview environment and associated resources are created/updated") {
          verify {
            repository.upsertResource(previewEnv.captured.resources.first(), deliveryConfig.name)
            repository.storeEnvironment(deliveryConfig.name, previewEnv.captured)
          }
        }

        test("the preview environment has no constraints or post-deploy actions") {
          expectThat(previewEnv.captured.constraints).isEmpty()
          expectThat(previewEnv.captured.postDeploy).isEmpty()
        }

        test("the name of the preview environment is generated correctly") {
          val baseEnv = deliveryConfig.environments.first()
          val branchDetail = commitEvent.targetBranch.toPreviewName()

          expectThat(previewEnv.captured) {
            get { name }.isEqualTo("${baseEnv.name}-$branchDetail")
          }
        }

        test("relevant metadata is added to the preview environment") {
          expectThat(previewEnv.captured.metadata).containsKeys("basedOn", "repoKey", "branch", "pullRequestId")
        }

        test("the name of monikered resources is generated correctly") {
          val baseEnv = deliveryConfig.environments.first()
          val baseResource = baseEnv.resources.first() as Resource<Monikered>
          val previewResource = previewEnv.captured.resources.first()

          expectThat(previewResource.spec)
            .isA<Monikered>()
            .get { moniker }
            .isEqualTo(baseResource.spec.moniker.withBranchDetail(commitEvent.targetBranch))
        }

        test("updated resource names respect the max allowed length") {
          // monikered resources with and without stack and detail
          listOf(
            locatableResource(moniker = Moniker(app = "fnord", stack = "stack", detail = "detail")),
            locatableResource(moniker = Moniker(app = "fnord", stack = "stack")),
            locatableResource(moniker = Moniker(app = "fnord")),
          ).forEach { resource ->
            val updatedName = resource.spec.moniker.withBranchDetail("feature/a-very-long-branch-name").name
            expectThat(updatedName.length)
              .describedAs("length of preview resource name $updatedName (${updatedName.length})")
              .isLessThanOrEqualTo(MAX_RESOURCE_NAME_LENGTH)
          }
        }

        test("the artifact reference in a resource is updated to match the preview environment branch filter") {
          expectThat(previewEnv.captured.resources.find { it.spec is ArtifactReferenceProvider }?.spec)
            .isA<ArtifactReferenceProvider>()
            .get { artifactReference }.isEqualTo(artifactFromBranch.reference)
        }

        test("the names of resource dependencies present in the preview environment are adjusted to match") {
          val branchDetail = commitEvent.targetBranch.toPreviewName()
          val baseEnv = deliveryConfig.environments.first()
          val dependency = baseEnv.resources.first { it.spec is DummyLocatableResourceSpec } as Resource<DummyLocatableResourceSpec>

          expectThat(previewEnv.captured.resources.first { it.spec is DummyDependentResourceSpec }.spec)
            .isA<Dependent>()
            .get { dependsOn.first { it.type == GENERIC_RESOURCE }.name }
            .isEqualTo(dependency.spec.moniker.withBranchDetail(branchDetail).name)
        }

        test("the name of the default security group is not changed in the dependencies") {
          expectThat(previewEnv.captured.resources.first { it.spec is DummyDependentResourceSpec }.spec)
            .isA<Dependent>()
            .get { dependsOn.first { it.type == SECURITY_GROUP }.name }
            .isEqualTo(defaultSecurityGroup.name)
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

        testEventIgnored()

        test("a delivery config not found is counted") {
          val tags = mutableListOf<Iterable<Tag>>()
          verify {
            spectator.counter(CODE_EVENT_COUNTER, capture(tags))
          }
          expectThat(tags).one {
            contains(DELIVERY_CONFIG_NOT_FOUND.toTags())
          }
        }

        test("duration metric is not recorded") {
          verify {
            fakeTimer wasNot called
          }
        }
      }

      context("a commit event not associated with a PR is received") {
        before {
          subject.handleCommitCreated(commitEvent.copy(pullRequestId = null))
        }

        testEventIgnored()
      }

      listOf(prMergedEvent, prDeclinedEvent, prDeletedEvent).forEach { prEvent ->
        context("a ${prEvent::class.simpleName} event matching a preview environment spec is received") {
          before {
            // just to trigger saving the preview environment
            subject.handleCommitCreated(commitEvent)

            every {
              repository.getDeliveryConfig(deliveryConfig.name)
            } returns deliveryConfig.copy(
              environments = deliveryConfig.environments + setOf(previewEnv.captured)
            )

            subject.handlePrFinished(prEvent)
          }

          test("the matching preview environment is marked for deletion") {
            verify {
              environmentDeletionRepository.markForDeletion(previewEnv.captured)
            }
          }

          test("a metric is counted for successfully marking for deletion") {
            val tags = mutableListOf<Iterable<Tag>>()
            verify {
              spectator.counter(CODE_EVENT_COUNTER, capture(tags))
            }
            expectThat(tags).one {
              contains(PREVIEW_ENVIRONMENT_MARK_FOR_DELETION_SUCCESS.toTags())
            }
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

    context("with feature flag disabled") {
      modifyFixture {
        every {
          springEnv.getProperty("keel.previewEnvironments.enabled", Boolean::class.java, true)
        } returns false
      }

      before {
        subject.handleCommitCreated(nonMatchingCommitEvent)
      }

      testEventIgnored()
    }

    context("other error scenarios") {
      before {
        setupMocks()
      }

      context("failure to retrieve delivery config") {
        modifyFixture {
          every {
            importer.import(commitEvent, "spinnaker.yml")
          } throws SystemException("oh noes!")
        }

        before {
          subject.handleCommitCreated(commitEvent)
        }

        test("a delivery config retrieval error is counted") {
          val tags = mutableListOf<Iterable<Tag>>()
          verify {
            spectator.counter(CODE_EVENT_COUNTER, capture(tags))
          }
          expectThat(tags).one {
            contains(DELIVERY_CONFIG_RETRIEVAL_ERROR.toTags())
          }
        }

        test("an event is published") {
          verify {
            eventPublisher.publishEvent(any<DeliveryConfigImportFailed>())
          }
        }
      }

      context("failure to retrieve application") {
        modifyFixture {
          every {
            front50Cache.applicationByName(deliveryConfig.application)
          } throws SystemException("oh noes!")
        }

        before {
          subject.handleCommitCreated(commitEvent)
        }

        test("an application retrieval error is counted") {
          val tags = mutableListOf<Iterable<Tag>>()
          verify {
            spectator.counter(CODE_EVENT_COUNTER, capture(tags))
          }
          expectThat(tags).one {
            contains(APPLICATION_RETRIEVAL_ERROR.toTags())
          }
        }
      }

      context("failure to usert preview environment") {
        modifyFixture {
          every {
            repository.storeEnvironment(any(), any())
          } throws SystemException("oh noes!")
        }

        before {
          subject.handleCommitCreated(commitEvent)
        }

        test("an upsert error is counted") {
          val tags = mutableListOf<Iterable<Tag>>()
          verify {
            spectator.counter(CODE_EVENT_COUNTER, capture(tags))
          }
          expectThat(tags).one {
            contains(PREVIEW_ENVIRONMENT_UPSERT_ERROR.toTags())
          }
        }
      }
    }
  }

  private fun TestContextBuilder<Fixture, Fixture>.testEventIgnored() {
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
