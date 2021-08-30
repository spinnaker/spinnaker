package com.netflix.spinnaker.keel.preview

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DependencyType.LOAD_BALANCER
import com.netflix.spinnaker.keel.api.DependencyType.SECURITY_GROUP
import com.netflix.spinnaker.keel.api.Dependent
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.PreviewEnvironmentSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.branchName
import com.netflix.spinnaker.keel.api.artifacts.branchStartsWith
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.EC2_SECURITY_GROUP_V1
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.api.ec2.old.ClusterV1Spec
import com.netflix.spinnaker.keel.api.ec2.old.ClusterV1Spec.ImageProvider
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.core.api.TagAmiPostDeployAction
import com.netflix.spinnaker.keel.core.name
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.DataSources
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.notifications.DeliveryConfigImportFailed
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
import com.netflix.spinnaker.keel.resources.ResourceFactory
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.scm.DELIVERY_CONFIG_RETRIEVAL_ERROR
import com.netflix.spinnaker.keel.scm.DELIVERY_CONFIG_RETRIEVAL_SUCCESS
import com.netflix.spinnaker.keel.scm.PrDeclinedEvent
import com.netflix.spinnaker.keel.scm.PrDeletedEvent
import com.netflix.spinnaker.keel.scm.PrMergedEvent
import com.netflix.spinnaker.keel.scm.PrOpenedEvent
import com.netflix.spinnaker.keel.scm.PrUpdatedEvent
import com.netflix.spinnaker.keel.scm.ScmUtils
import com.netflix.spinnaker.keel.scm.toTags
import com.netflix.spinnaker.keel.test.DummyArtifactReferenceResourceSpec
import com.netflix.spinnaker.keel.test.DummyLocatableResourceSpec
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.applicationLoadBalancer
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.debianArtifact
import com.netflix.spinnaker.keel.test.dockerArtifact
import com.netflix.spinnaker.keel.test.locatableResource
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.test.submittedResource
import com.netflix.spinnaker.keel.test.titusCluster
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
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactlyInAnyOrder
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
    val scmUtils: ScmUtils = mockk()

    // need to copy/paste this here because it's in keel-ec2-plugin
    val ec2ClusterMigrator = object : SpecMigrator<ClusterV1Spec, ClusterSpec> {
      override val input = EC2_CLUSTER_V1
      override val output = EC2_CLUSTER_V1_1
      override fun migrate(spec: ClusterV1Spec): ClusterSpec =
        with(spec) {
          ClusterSpec(
            moniker = moniker,
            artifactReference = imageProvider?.reference,
            deployWith = deployWith,
            locations = locations,
            _defaults = defaults,
            overrides = overrides
          )
        }
      }

    val resourceFactory: ResourceFactory = spyk(
      ResourceFactory(
        objectMapper = objectMapper,
        resourceSpecIdentifier = ResourceSpecIdentifier(EC2_CLUSTER_V1, EC2_CLUSTER_V1_1),
        specMigrators = listOf(ec2ClusterMigrator)
      )
    )

    val subject = spyk(
      PreviewEnvironmentCodeEventListener(
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
        eventPublisher = eventPublisher,
        scmUtils = scmUtils,
        resourceFactory = resourceFactory
      )
    )

    val appConfig = Application(
      name = "fnord",
      email = "keel@keel.io",
      repoType = "stash",
      repoProjectKey = "myorg",
      repoSlug = "myrepo",
      dataSources = DataSources(enabled = emptyList(), disabled = emptyList())
    )

    val dockerFromMain = dockerArtifact()

    val dockerFromBranch = dockerFromMain.copy(
      reference = "docker-from-branch",
      from = ArtifactOriginFilter(branch = branchStartsWith("feature/"))
    )

    val dockerWithNonMatchingFilter = dockerFromBranch.copy(
      reference = "fnord-non-matching",
      from = ArtifactOriginFilter(branch = branchName("not-the-right-branch"))
    )

    val applicationLoadBalancer = applicationLoadBalancer()

    val cluster = titusCluster(artifact = dockerFromMain)

    val defaultAppSecurityGroup = Resource(
      kind = EC2_SECURITY_GROUP_V1.kind,
      metadata = mapOf("id" to "fnord", "application" to "fnord"),
      spec = SecurityGroupSpec(
        moniker = Moniker("fnord"),
        locations = cluster.spec.locations,
        description = "default app security group"
      )
    )

    val defaultElbSecurityGroup = Resource(
      kind = EC2_SECURITY_GROUP_V1.kind,
      metadata = mapOf("id" to "fnord", "application" to "fnord"),
      spec = SecurityGroupSpec(
        moniker = Moniker("fnord", "elb"),
        locations = cluster.spec.locations,
        description = "default load balancer security group"
      )
    )

    val clusterNamedAfterApp = titusCluster(
      moniker = Moniker("fnord"),
      artifact = dockerFromMain
    )

    val clusterWithDependencies = titusCluster(
      moniker = Moniker("fnord", "dependent"),
      artifact = dockerFromMain
    ).run {
      copy(
        spec = spec.copy(
          _defaults = spec.defaults.copy(
            dependencies = ClusterDependencies(
              loadBalancerNames = setOf(applicationLoadBalancer.name),
              securityGroupNames = setOf(defaultAppSecurityGroup.name, defaultElbSecurityGroup.name)
            )
          )
        )
      )
    }

    val debianFromMain = debianArtifact()
    val debianFromBranch = debianFromMain.copy(
      reference = "debian-from-branch",
      from = ArtifactOriginFilter(branchStartsWith("feature/"))
    )

    val clusterWithOldSpecVersion = resource(
      kind = EC2_CLUSTER_V1.kind,
      spec = ClusterV1Spec(
        moniker = Moniker("fnord", "old"),
        imageProvider = ImageProvider(debianFromMain.reference),
        locations = applicationLoadBalancer.spec.locations
      )
    )

    var deliveryConfig = DeliveryConfig(
      application = "fnord",
      name = "myconfig",
      serviceAccount = "keel@keel.io",
      artifacts = setOf(dockerFromMain, dockerFromBranch, debianFromMain, debianFromBranch),
      environments = setOf(
        Environment(
          name = "test",
          resources = setOf(
            applicationLoadBalancer,
            cluster,
            clusterNamedAfterApp, // name conflict with default sec group, but different kind
            defaultAppSecurityGroup,
            defaultElbSecurityGroup,
            clusterWithDependencies,
            clusterWithOldSpecVersion
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
        front50Cache.applicationByName(deliveryConfig.application)
      } returns appConfig

      every {
        importer.import(any(), manifestPath = any())
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

      every {
        scmUtils.getPullRequestLink(any())
      } returns "https://commit-link.org"
    }
  }

  val prOpenedEvent = PrOpenedEvent(
    repoKey = "stash/myorg/myrepo",
    pullRequestBranch = "feature/abc",
    targetBranch = "main",
    pullRequestId = "42"
  )

  val prUpdatedEvent = PrUpdatedEvent(
    repoKey = "stash/myorg/myrepo",
    pullRequestBranch = "feature/abc",
    targetBranch = "main",
    pullRequestId = "42"
  )

  val prMergedEvent = PrMergedEvent(
    repoKey = "stash/myorg/myrepo",
    pullRequestBranch = "feature/abc",
    targetBranch = "main",
    pullRequestId = "42",
    commitHash = "a34afb13b"
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

      listOf(prOpenedEvent, prUpdatedEvent).forEach { prEvent ->
        context("a PR event matching a preview environment spec is received") {
          before {
            subject.handlePrEvent(prEvent)
          }

          test("the delivery config is imported from the branch in the PR event") {
            verify(exactly = 1) {
              importer.import(
                codeEvent = any(),
                manifestPath = any(),
              )
            }
          }

          test("delivery config import failure notification is dismissed on successful import") {
            verify {
              notificationRepository.dismissNotification(
                any<Class<DismissibleNotification>>(),
                deliveryConfig.application,
                prEvent.pullRequestBranch,
                any()
              )
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

          test("a duration is recorded for successful handling of the PR event") {
            verify(exactly = 1) {
              spectator.timer(COMMIT_HANDLING_DURATION, any<Iterable<Tag>>())
              fakeTimer.record(any<Duration>())
            }
          }

          test("a preview environment and associated resources are created/updated") {
            previewEnv.captured.resources.forEach { previewResource ->
              verify {
                repository.upsertResource(previewResource, deliveryConfig.name)
              }
            }
            verify {
              repository.storeEnvironment(deliveryConfig.name, previewEnv.captured)
            }
          }

          test("the preview environment has no constraints or post-deploy actions") {
            expectThat(previewEnv.captured.constraints).isEmpty()
            expectThat(previewEnv.captured.postDeploy).isEmpty()
          }

          test("the name of the preview environment is generated correctly") {
            val baseEnv = deliveryConfig.environments.first()
            val branchDetail = prEvent.pullRequestBranch.toPreviewName()

            expectThat(previewEnv.captured) {
              get { name }.isEqualTo("${baseEnv.name}-$branchDetail")
            }
          }

          test("relevant metadata is added to the preview environment") {
            expectThat(previewEnv.captured.metadata).containsKeys("basedOn", "repoKey", "branch", "pullRequestId")
          }

          test("resources are migrated to their latest version before processing") {
            val baseEnv = deliveryConfig.environments.first()
            verify(exactly = baseEnv.resources.size) {
              resourceFactory.migrate(any())
            }
            @Suppress("DEPRECATION")
            expect {
              that(clusterWithOldSpecVersion.kind)
                .isEqualTo(EC2_CLUSTER_V1.kind)
              that(previewEnv.captured.resources.find { it.basedOn == clusterWithOldSpecVersion.id }!!.kind)
                .isEqualTo(EC2_CLUSTER_V1_1.kind)
            }
          }

          test("the name of monikered resources is updated with branch detail") {
            val baseEnv = deliveryConfig.environments.first()
            val baseResource = baseEnv.resources.first() as Resource<Monikered>
            val previewResource = previewEnv.captured.resources.first()

            expectThat(previewResource.spec)
              .isA<Monikered>()
              .get { moniker }
              .isEqualTo(subject.withBranchDetail(baseResource, prEvent.pullRequestBranch).spec.moniker)
          }

          test("updated resource names respect the max allowed length") {
            // monikered resources with and without stack and detail
            listOf(
              locatableResource(moniker = Moniker(app = "fnord", stack = "stack", detail = "detail")),
              locatableResource(moniker = Moniker(app = "fnord", stack = "stack")),
              locatableResource(moniker = Moniker(app = "fnord")),
            ).forEach { resource ->
              val updatedName = subject.withBranchDetail(resource, "feature/a-very-long-branch-name").name
              expectThat(updatedName.length)
                .describedAs("length of preview resource name $updatedName (${updatedName.length})")
                .isLessThanOrEqualTo(MAX_RESOURCE_NAME_LENGTH)
            }
          }

          test("updated resource names are DNS-compatible") {
            val resource = locatableResource(moniker = Moniker(app = "fnord"))
            val updatedName = subject.withBranchDetail(resource, "feature/a_branch_name_with_underscores").name
            expectThat(updatedName).not().contains("_")
          }

          test("the artifact reference in a resource is updated to match the preview environment branch filter") {
            expectThat(previewEnv.captured.resources.find { it.basedOn == cluster.id }?.spec)
              .isA<ArtifactReferenceProvider>()
              .get { artifactReference }.isEqualTo(dockerFromBranch.reference)

            expectThat(previewEnv.captured.resources.find { it.basedOn == clusterWithOldSpecVersion.id }?.spec)
              // this also demonstrates that the old cluster spec gets migrated and now supports the standard artifact reference interface
              .isA<ArtifactReferenceProvider>()
              .get { artifactReference }.isEqualTo(debianFromBranch.reference)
          }

          test("the names of resource dependencies present in the preview environment are adjusted to match") {
            val branchDetail = prEvent.pullRequestBranch.toPreviewName()
            val dependency = applicationLoadBalancer

            expectThat(previewEnv.captured.resources.find { it.basedOn == clusterWithDependencies.id }?.spec)
              .isA<Dependent>()
              .get { dependsOn.first { it.type == LOAD_BALANCER }.name }
              .isEqualTo(dependency.spec.moniker.withBranchDetail(branchDetail).name)
          }

          test("the names of the default security groups are not changed in the dependencies") {
            expectThat(previewEnv.captured.resources.find { it.basedOn == clusterWithDependencies.id }?.spec)
              .isA<Dependent>()
              .get { dependsOn.filter { it.type == SECURITY_GROUP }.map { it.name }.toSet() }
              .containsExactlyInAnyOrder(defaultAppSecurityGroup.name, defaultElbSecurityGroup.name)
          }
        }

        context("without an artifact in the delivery config matching the branch filter") {
          modifyFixture {
            deliveryConfig = deliveryConfig.run {
              copy(
                artifacts = artifacts.map {
                  if (it == dockerFromBranch) dockerWithNonMatchingFilter else it
                }.toSet()
              )
            }
          }

          before {
            setupMocks() // to pick up the updated delivery config above
            subject.handlePrEvent(prEvent)
          }

          test("the artifact reference in a resource is not updated") {
            expectThat(previewEnv.captured.resources.find { it.basedOn == cluster.id }?.spec)
              .isA<ArtifactReferenceProvider>()
              .get { artifactReference }.isEqualTo(dockerFromMain.reference)
          }
        }
      }

      context("an app with custom manifest path") {
        val manifestPath = "custom/spinnaker.yml"

        before {
          every {
            front50Cache.applicationByName(deliveryConfig.application)
          } returns appConfig.copy(managedDelivery = ManagedDeliveryConfig(manifestPath = manifestPath))

        }

        test("importing the manifest from the correct path") {
          subject.handlePrEvent(prOpenedEvent)
          verify(exactly = 1) {
            importer.import(
              codeEvent = any(),
              manifestPath = manifestPath
            )
          }
        }
      }

      context("a PR event NOT matching a preview environment spec is received") {
        before {
          val nonMatchingPrEvent = prOpenedEvent.copy(pullRequestBranch = "not-a-match")
          subject.handlePrEvent(nonMatchingPrEvent)
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

      context("a PR event not associated with a PR is received") {
        before {
          subject.handlePrEvent(prOpenedEvent.copy(pullRequestId = "-1"))
        }

        testEventIgnored()
      }

      listOf(prMergedEvent, prDeclinedEvent, prDeletedEvent).forEach { prEvent ->
        context("a ${prEvent::class.simpleName} event matching a preview environment spec is received") {
          before {
            // just to trigger saving the preview environment
            subject.handlePrEvent(prEvent)

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

    context("a PR event matching a preview spec branch filter but from a different app's repo") {
      before {
        setupMocks()

        every {
          front50Cache.applicationByName("fnord")
        } returns appConfig.copy(
          repoProjectKey = "anotherorg",
          repoSlug = "another-repo"
        )

        subject.handlePrEvent(prOpenedEvent)
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
        val nonMatchingPrEvent = prOpenedEvent.copy(pullRequestBranch = "not-a-match")
        subject.handlePrEvent(nonMatchingPrEvent)
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
            importer.import(any(), manifestPath = any())
          } throws SystemException("oh noes!")
        }

        before {
          subject.handlePrEvent(prOpenedEvent)
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
          subject.handlePrEvent(prOpenedEvent)
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

      context("failure to upsert preview environment") {
        modifyFixture {
          every {
            repository.storeEnvironment(any(), any())
          } throws SystemException("oh noes!")
        }

        before {
          subject.handlePrEvent(prOpenedEvent)
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
