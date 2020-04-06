package com.netflix.spinnaker.keel

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ReferenceArtifactImageProvider
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.ConstraintState
import com.netflix.spinnaker.keel.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.constraints.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.ArtifactVersionStatus
import com.netflix.spinnaker.keel.core.api.ArtifactVersionSummary
import com.netflix.spinnaker.keel.core.api.ArtifactVersions
import com.netflix.spinnaker.keel.core.api.BuildMetadata
import com.netflix.spinnaker.keel.core.api.DependOnConstraintMetadata
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.GitMetadata
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.PipelineConstraint
import com.netflix.spinnaker.keel.core.api.StatefulConstraintSummary
import com.netflix.spinnaker.keel.core.api.StatelessConstraintSummary
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.ResourceStatus.HAPPY
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.services.ApplicationService
import com.netflix.spinnaker.keel.test.combinedInMemoryRepository
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull

class ApplicationServiceTests : JUnit5Minutests {
  class Fixture {
    val clock: MutableClock = MutableClock(
      Instant.parse("2020-03-25T00:00:00.00Z"),
      ZoneId.of("UTC")
    )
    val deliveryConfigRepository = InMemoryDeliveryConfigRepository(clock)
    val artifactRepository = InMemoryArtifactRepository(clock)
    val resourceRepository = InMemoryResourceRepository(clock)
    val repository: KeelRepository = combinedInMemoryRepository(
      deliveryConfigRepository = deliveryConfigRepository,
      artifactRepository = artifactRepository,
      resourceRepository = resourceRepository,
      clock = clock
    )

    val application = "fnord"
    val artifact = DebianArtifact(
      name = application,
      deliveryConfigName = "manifest",
      reference = "fnord",
      statuses = setOf(ArtifactStatus.RELEASE),
      vmOptions = VirtualMachineOptions(
        baseOs = "xenial",
        regions = setOf("us-west-2", "us-east-1")
      )
    )

    val clusterDefaults = ClusterSpec.ServerGroupSpec(
      launchConfiguration = ClusterSpec.LaunchConfigurationSpec(
        instanceType = "m4.2xlarge",
        ebsOptimized = true,
        iamRole = "fnordInstanceProfile",
        keyPair = "fnordKeyPair"
      )
    )

    val environments = listOf("test", "staging", "production").associateWith { name ->
      Environment(
        name = name,
        constraints = if (name == "production") {
          setOf(
            DependsOnConstraint("staging"),
            ManualJudgementConstraint(),
            PipelineConstraint(pipelineId = "fakePipeline")
          )
        } else {
          emptySet()
        },
        resources = setOf(
          // cluster with new-style artifact reference
          resource(
            kind = SPINNAKER_EC2_API_V1.qualify("cluster"),
            spec = ClusterSpec(
              moniker = Moniker(application, "$name"),
              imageProvider = ReferenceArtifactImageProvider(reference = "fnord"),
              locations = SubnetAwareLocations(
                account = "test",
                vpc = "vpc0",
                subnet = "internal (vpc0)",
                regions = setOf(
                  SubnetAwareRegionSpec(
                    name = "us-west-2",
                    availabilityZones = setOf("us-west-2a", "us-west-2b", "us-west-2c")
                  )
                )
              ),
              _defaults = clusterDefaults
            )
          ),
          // cluster with old-style image provider
          resource(
            kind = SPINNAKER_EC2_API_V1.qualify("cluster"),
            spec = ClusterSpec(
              moniker = Moniker(application, "$name-east"),
              imageProvider = ArtifactImageProvider(deliveryArtifact = artifact),
              locations = SubnetAwareLocations(
                account = "test",
                vpc = "vpc0",
                subnet = "internal (vpc0)",
                regions = setOf(
                  SubnetAwareRegionSpec(
                    name = "us-east-1",
                    availabilityZones = setOf("us-east-1a", "us-east-1b", "us-east-1c")
                  )
                )
              ),
              _defaults = clusterDefaults
            )
          )
        )
      )
    }

    val deliveryConfig = DeliveryConfig(
      name = "manifest",
      application = application,
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = environments.values.toSet()
    )

    val statelessEvaluator = mockk<ConstraintEvaluator<*>>() {
      every { supportedType } returns SupportedConstraintType<DependsOnConstraint>("depends-on")
      every { isImplicit() } returns false
      every { canPromote(any(), any(), any(), any()) } answers {
        secondArg<String>() in listOf("fnord-1.0.0-h0.a0a0a0a", "fnord-1.0.1-h1.b1b1b1b")
      }
    }

    // subject
    val applicationService = ApplicationService(repository, listOf(statelessEvaluator))
  }

  fun applicationServiceTests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    before {
      clock.reset()
    }

    after {
      deliveryConfigRepository.dropAll()
      artifactRepository.dropAll()
      resourceRepository.dropAll()
      clearAllMocks()
    }

    context("delivery config exists and there has been activity") {
      before {
        repository.upsertDeliveryConfig(deliveryConfig)
        // these events are required because Resource.toResourceSummary() relies on events to determine resource status
        deliveryConfig.environments.flatMap { it.resources }.forEach { resource ->
          repository.resourceAppendHistory(ResourceValid(resource))
        }
        repository.storeArtifact(artifact, "fnord-1.0.0-h0.a0a0a0a", ArtifactStatus.RELEASE)
        repository.storeArtifact(artifact, "fnord-1.0.1-h1.b1b1b1b", ArtifactStatus.RELEASE)
        repository.storeArtifact(artifact, "fnord-1.0.2-h2.c2c2c2c", ArtifactStatus.RELEASE)
        repository.storeArtifact(artifact, "fnord-1.0.3-h3.d3d3d3d", ArtifactStatus.RELEASE)

        // with our fake clock moving forward, simulate artifact approvals and deployments
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.0-h0.a0a0a0a", "test")
        clock.tickHours(1) // 2020-03-25T01:00:00.00Z
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.0-h0.a0a0a0a", "staging")
        val productionDeployed = clock.tickHours(1) // 2020-03-25T02:00:00.00Z
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.0-h0.a0a0a0a", "production")
        clock.tickHours(1) // 2020-03-25T03:00:00.00Z
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.1-h1.b1b1b1b", "test")
        clock.tickHours(1) // 2020-03-25T04:00:00.00Z
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.1-h1.b1b1b1b", "staging")
        clock.tickHours(1) // 2020-03-25T05:00:00.00Z
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.2-h2.c2c2c2c", "test")
        repository.approveVersionFor(deliveryConfig, artifact, "fnord-1.0.3-h3.d3d3d3d", "test")
        repository.storeConstraintState(
          ConstraintState(
            deliveryConfigName = deliveryConfig.name,
            environmentName = "production",
            artifactVersion = "fnord-1.0.0-h0.a0a0a0a",
            type = "manual-judgement",
            status = ConstraintStatus.OVERRIDE_PASS,
            createdAt = clock.start,
            judgedAt = productionDeployed.minus(Duration.ofMinutes(30)),
            judgedBy = "lpollo@acme.com",
            comment = "Aye!"
          )
        )
      }

      test("can get resource summary by application") {
        val summaries = applicationService.getResourceSummariesFor(application)

        expect {
          that(summaries.size).isEqualTo(6)
          that(summaries.map { it.status }.filter { it == HAPPY }.size).isEqualTo(6)
          that(summaries.map { it.moniker?.stack }).containsExactlyInAnyOrder("test", "staging", "production", "test-east", "staging-east", "production-east")
        }
      }

      test("can get environment summaries by application") {
        val summaries = applicationService.getEnvironmentSummariesFor(application)

        val production = summaries.find { it.name == "production" }
        val staging = summaries.find { it.name == "staging" }
        val test = summaries.find { it.name == "test" }

        val expectedProd = EnvironmentSummary(
          deliveryConfig.environments.find { it.name == "production" }!!,
          setOf(ArtifactVersions(
            artifact.name,
            artifact.type,
            artifact.statuses,
            ArtifactVersionStatus(
              current = "fnord-1.0.0-h0.a0a0a0a",
              pending = listOf("fnord-1.0.1-h1.b1b1b1b", "fnord-1.0.2-h2.c2c2c2c", "fnord-1.0.3-h3.d3d3d3d"),
              approved = listOf(),
              previous = listOf(),
              vetoed = listOf(),
              deploying = null,
              skipped = listOf()
            )
          ))
        )
        val expectedStage = EnvironmentSummary(
          deliveryConfig.environments.find { it.name == "staging" }!!,
          setOf(ArtifactVersions(
            artifact.name,
            artifact.type,
            artifact.statuses,
            ArtifactVersionStatus(
              current = "fnord-1.0.1-h1.b1b1b1b",
              pending = listOf("fnord-1.0.2-h2.c2c2c2c", "fnord-1.0.3-h3.d3d3d3d"),
              approved = listOf(),
              previous = listOf("fnord-1.0.0-h0.a0a0a0a"),
              vetoed = listOf(),
              deploying = null,
              skipped = listOf()
            )
          ))
        )
        val expectedTest = EnvironmentSummary(
          deliveryConfig.environments.find { it.name == "test" }!!,
          setOf(ArtifactVersions(
            artifact.name,
            artifact.type,
            artifact.statuses,
            ArtifactVersionStatus(
              current = "fnord-1.0.2-h2.c2c2c2c",
              pending = listOf(),
              approved = listOf("fnord-1.0.3-h3.d3d3d3d"),
              previous = listOf("fnord-1.0.0-h0.a0a0a0a", "fnord-1.0.1-h1.b1b1b1b"),
              vetoed = listOf(),
              deploying = null,
              skipped = listOf()
            )
          ))
        )
        expect {
          that(summaries.size).isEqualTo(3)
          that(production).isNotNull()
          that(production).isEqualTo(expectedProd)
          that(staging).isNotNull()
          that(staging).isEqualTo(expectedStage)
          that(test).isNotNull()
          that(test).isEqualTo(expectedTest)
        }
      }

      test("can get artifact summaries by application") {
        val summaries = applicationService.getArtifactSummariesFor(application)
        val v3 = ArtifactVersionSummary(
          version = "fnord-1.0.3-h3.d3d3d3d",
          displayName = "1.0.3",
          environments = setOf(
            ArtifactSummaryInEnvironment(environment = "test", version = "fnord-1.0.3-h3.d3d3d3d", state = "approved"),
            ArtifactSummaryInEnvironment(environment = "staging", version = "fnord-1.0.3-h3.d3d3d3d", state = "pending"),
            ArtifactSummaryInEnvironment(environment = "production", version = "fnord-1.0.3-h3.d3d3d3d", state = "pending",
              statefulConstraints = listOf(StatefulConstraintSummary("manual-judgement", NOT_EVALUATED), StatefulConstraintSummary("pipeline", NOT_EVALUATED)),
              statelessConstraints = listOf(StatelessConstraintSummary("depends-on", false, DependOnConstraintMetadata("staging"))))
          ),
          build = BuildMetadata(3),
          git = GitMetadata("d3d3d3d")
        )
        val v2 = ArtifactVersionSummary(
          version = "fnord-1.0.2-h2.c2c2c2c",
          displayName = "1.0.2",
          environments = setOf( // todo eb: this is changing every time (deployed at time...
            ArtifactSummaryInEnvironment(environment = "test", version = "fnord-1.0.2-h2.c2c2c2c", state = "current", deployedAt = Instant.parse("2020-03-25T05:00:00Z")),
            ArtifactSummaryInEnvironment(environment = "staging", version = "fnord-1.0.2-h2.c2c2c2c", state = "pending"),
            ArtifactSummaryInEnvironment(environment = "production", version = "fnord-1.0.2-h2.c2c2c2c", state = "pending",
              statefulConstraints = listOf(StatefulConstraintSummary("manual-judgement", NOT_EVALUATED), StatefulConstraintSummary("pipeline", NOT_EVALUATED)),
              statelessConstraints = listOf(StatelessConstraintSummary("depends-on", false, DependOnConstraintMetadata("staging"))))
          ),
          build = BuildMetadata(2),
          git = GitMetadata("c2c2c2c")
        )
        val v1 = ArtifactVersionSummary(
          version = "fnord-1.0.1-h1.b1b1b1b",
          displayName = "1.0.1",
          environments = setOf(
            ArtifactSummaryInEnvironment(environment = "test", version = "fnord-1.0.1-h1.b1b1b1b", state = "previous", deployedAt = Instant.parse("2020-03-25T03:00:00Z"), replacedAt = Instant.parse("2020-03-25T05:00:00Z"), replacedBy = "fnord-1.0.2-h2.c2c2c2c"),
            ArtifactSummaryInEnvironment(environment = "staging", version = "fnord-1.0.1-h1.b1b1b1b", state = "current", deployedAt = Instant.parse("2020-03-25T04:00:00Z")),
            ArtifactSummaryInEnvironment(environment = "production", version = "fnord-1.0.1-h1.b1b1b1b", state = "pending",
              statefulConstraints = listOf(StatefulConstraintSummary("manual-judgement", NOT_EVALUATED), StatefulConstraintSummary("pipeline", NOT_EVALUATED)),
              statelessConstraints = listOf(StatelessConstraintSummary("depends-on", true, DependOnConstraintMetadata("staging")))
            )
          ),
          build = BuildMetadata(1),
          git = GitMetadata("b1b1b1b")
        )
        val v0 = ArtifactVersionSummary(
          version = "fnord-1.0.0-h0.a0a0a0a",
          displayName = "1.0.0",
          environments = setOf(
            ArtifactSummaryInEnvironment(environment = "test", version = "fnord-1.0.0-h0.a0a0a0a", state = "previous", deployedAt = Instant.parse("2020-03-25T00:00:00Z"), replacedAt = Instant.parse("2020-03-25T03:00:00Z"), replacedBy = "fnord-1.0.1-h1.b1b1b1b"),
            ArtifactSummaryInEnvironment(environment = "staging", version = "fnord-1.0.0-h0.a0a0a0a", state = "previous", deployedAt = Instant.parse("2020-03-25T01:00:00Z"), replacedAt = Instant.parse("2020-03-25T04:00:00Z"), replacedBy = "fnord-1.0.1-h1.b1b1b1b"),
            ArtifactSummaryInEnvironment(environment = "production", version = "fnord-1.0.0-h0.a0a0a0a", state = "current", deployedAt = Instant.parse("2020-03-25T02:00:00Z"),
              statefulConstraints = listOf(StatefulConstraintSummary("manual-judgement", OVERRIDE_PASS, startedAt = Instant.parse("2020-03-25T00:00:00Z"), judgedBy = "lpollo@acme.com", judgedAt = Instant.parse("2020-03-25T01:30:00Z"), comment = "Aye!"), StatefulConstraintSummary("pipeline", NOT_EVALUATED)),
              statelessConstraints = listOf(StatelessConstraintSummary("depends-on", true, DependOnConstraintMetadata("staging"))))
          ),
          build = BuildMetadata(0),
          git = GitMetadata("a0a0a0a")
        )

        expect {
          that(summaries.size).isEqualTo(1)
          that(summaries.first().versions.find { it.version == "fnord-1.0.3-h3.d3d3d3d" }).isEqualTo(v3)
          that(summaries.first().versions.find { it.version == "fnord-1.0.2-h2.c2c2c2c" }).isEqualTo(v2)
          that(summaries.first().versions.find { it.version == "fnord-1.0.1-h1.b1b1b1b" }).isEqualTo(v1)
          that(summaries.first().versions.find { it.version == "fnord-1.0.0-h0.a0a0a0a" }).isEqualTo(v0)
        }
      }

      test("no constraints have been evaluated") {
        val states = applicationService.getConstraintStatesFor(application, "prod", 10)
        expectThat(states).isEmpty()
      }

      test("pending manual judgement") {
        val judgement = ConstraintState(deliveryConfig.name, "production", "fnord-1.0.2-h2.c2c2c2c", "manual-judgement", PENDING)
        repository.storeConstraintState(judgement)

        val states = applicationService.getConstraintStatesFor(application, "production", 10)
        expect {
          that(states).isNotEmpty()
          that(states.size).isEqualTo(2)
          that(states.first().status).isEqualTo(PENDING)
          that(states.first().type).isEqualTo("manual-judgement")
        }
      }

      test("approve manual judgement") {
        val judgement = ConstraintState(deliveryConfig.name, "production", "fnord-1.0.2-h2.c2c2c2c", "manual-judgement", PENDING)
        repository.storeConstraintState(judgement)

        val updatedState = UpdatedConstraintStatus("manual-judgement", "fnord-1.0.2-h2.c2c2c2c", OVERRIDE_PASS)
        applicationService.updateConstraintStatus("keel", application, "production", updatedState)

        val states = applicationService.getConstraintStatesFor(application, "production", 10)
        expect {
          that(states).isNotEmpty()
          that(states.size).isEqualTo(2)
          that(states.first().status).isEqualTo(OVERRIDE_PASS)
          that(states.first().type).isEqualTo("manual-judgement")
        }
      }
    }
  }
}
