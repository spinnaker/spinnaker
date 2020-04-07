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
import com.netflix.spinnaker.keel.constraints.ConstraintState
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.constraints.DependsOnConstraintEvaluator
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

    val version0 = "fnord-1.0.0-h0.a0a0a0a"
    val version1 = "fnord-1.0.1-h1.b1b1b1b"
    val version2 = "fnord-1.0.2-h2.c2c2c2c"
    val version3 = "fnord-1.0.3-h3.d3d3d3d"
    val version4 = "fnord-1.0.4-h4.e4e4e4e"

    val dependsOnEvaluator = DependsOnConstraintEvaluator(artifactRepository, mockk())

    // subject
    val applicationService = ApplicationService(repository, listOf(dependsOnEvaluator))
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
        repository.storeArtifact(artifact, version0, ArtifactStatus.RELEASE)
        repository.storeArtifact(artifact, version1, ArtifactStatus.RELEASE)
        repository.storeArtifact(artifact, version2, ArtifactStatus.RELEASE)
        repository.storeArtifact(artifact, version3, ArtifactStatus.RELEASE)
        repository.storeArtifact(artifact, version4, ArtifactStatus.RELEASE)

        // with our fake clock moving forward, simulate artifact approvals and deployments
        // v0
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, version0, "test")
        clock.tickHours(1) // 2020-03-25T01:00:00.00Z
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, version0, "staging")
        val productionDeployed = clock.tickHours(1) // 2020-03-25T02:00:00.00Z
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, version0, "production")

        // v1 skipped by v2
        clock.tickHours(1) // 2020-03-25T03:00:00.00Z
        repository.markAsSkipped(deliveryConfig, artifact, version1, "test", version2)
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, version2, "test")
        clock.tickHours(1) // 2020-03-25T04:00:00.00Z
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, version2, "staging")
        clock.tickHours(1) // 2020-03-25T05:00:00.00Z
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, version3, "test")

        repository.approveVersionFor(deliveryConfig, artifact, version4, "test")
        repository.storeConstraintState(
          ConstraintState(
            deliveryConfigName = deliveryConfig.name,
            environmentName = "production",
            artifactVersion = version0,
            type = "manual-judgement",
            status = OVERRIDE_PASS,
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
              current = version0,
              pending = listOf(version1, version2, version3, version4),
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
              current = version2,
              pending = listOf(version3, version4),
              approved = listOf(),
              previous = listOf(version0),
              vetoed = listOf(),
              deploying = null,
              skipped = listOf(version1)
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
              current = version3,
              pending = listOf(),
              approved = listOf(version4),
              previous = listOf(version0, version2),
              vetoed = listOf(),
              deploying = null,
              skipped = listOf(version1)
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
        val v4 = ArtifactVersionSummary(
          version = version4,
          displayName = "1.0.4",
          environments = setOf(
            ArtifactSummaryInEnvironment(environment = "test", version = version4, state = "approved"),
            ArtifactSummaryInEnvironment(environment = "staging", version = version4, state = "pending"),
            ArtifactSummaryInEnvironment(environment = "production", version = version4, state = "pending",
              statefulConstraints = listOf(StatefulConstraintSummary("manual-judgement", NOT_EVALUATED), StatefulConstraintSummary("pipeline", NOT_EVALUATED)),
              statelessConstraints = listOf(StatelessConstraintSummary("depends-on", false, DependOnConstraintMetadata("staging"))))
          ),
          build = BuildMetadata(4),
          git = GitMetadata("e4e4e4e")
        )
        val v3 = ArtifactVersionSummary(
          version = version3,
          displayName = "1.0.3",
          environments = setOf(
            ArtifactSummaryInEnvironment(environment = "test", version = version3, state = "current", deployedAt = Instant.parse("2020-03-25T05:00:00Z")),
            ArtifactSummaryInEnvironment(environment = "staging", version = version3, state = "pending"),
            ArtifactSummaryInEnvironment(environment = "production", version = version3, state = "pending",
              statefulConstraints = listOf(StatefulConstraintSummary("manual-judgement", NOT_EVALUATED), StatefulConstraintSummary("pipeline", NOT_EVALUATED)),
              statelessConstraints = listOf(StatelessConstraintSummary("depends-on", false, DependOnConstraintMetadata("staging"))))
          ),
          build = BuildMetadata(3),
          git = GitMetadata("d3d3d3d")
        )
        val v2 = ArtifactVersionSummary(
          version = version2,
          displayName = "1.0.2",
          environments = setOf(
            ArtifactSummaryInEnvironment(environment = "test", version = version2, state = "previous", deployedAt = Instant.parse("2020-03-25T03:00:00Z"), replacedAt = Instant.parse("2020-03-25T05:00:00Z"), replacedBy = version3),
            ArtifactSummaryInEnvironment(environment = "staging", version = version2, state = "current", deployedAt = Instant.parse("2020-03-25T04:00:00Z")),
            ArtifactSummaryInEnvironment(environment = "production", version = version2, state = "pending",
              statefulConstraints = listOf(StatefulConstraintSummary("manual-judgement", NOT_EVALUATED), StatefulConstraintSummary("pipeline", NOT_EVALUATED)),
              statelessConstraints = listOf(StatelessConstraintSummary("depends-on", true, DependOnConstraintMetadata("staging")))
            )
          ),
          build = BuildMetadata(2),
          git = GitMetadata("c2c2c2c")
        )
        val v1 = ArtifactVersionSummary(
          version = version1,
          displayName = "1.0.1",
          environments = setOf(
            ArtifactSummaryInEnvironment(environment = "test", version = version1, state = "skipped", replacedBy = version2, replacedAt = Instant.parse("2020-03-25T03:00:00Z")),
            ArtifactSummaryInEnvironment(environment = "staging", version = version1, state = "skipped"),
            ArtifactSummaryInEnvironment(environment = "production", version = version1, state = "pending",
              statefulConstraints = listOf(StatefulConstraintSummary("manual-judgement", NOT_EVALUATED), StatefulConstraintSummary("pipeline", NOT_EVALUATED)),
              statelessConstraints = listOf(StatelessConstraintSummary("depends-on", false, DependOnConstraintMetadata("staging")))
            )
          ),
          build = BuildMetadata(1),
          git = GitMetadata("b1b1b1b")
        )
        val v0 = ArtifactVersionSummary(
          version = version0,
          displayName = "1.0.0",
          environments = setOf(
            ArtifactSummaryInEnvironment(environment = "test", version = version0, state = "previous", deployedAt = Instant.parse("2020-03-25T00:00:00Z"), replacedAt = Instant.parse("2020-03-25T03:00:00Z"), replacedBy = version2),
            ArtifactSummaryInEnvironment(environment = "staging", version = version0, state = "previous", deployedAt = Instant.parse("2020-03-25T01:00:00Z"), replacedAt = Instant.parse("2020-03-25T04:00:00Z"), replacedBy = version2),
            ArtifactSummaryInEnvironment(environment = "production", version = version0, state = "current", deployedAt = Instant.parse("2020-03-25T02:00:00Z"),
              statefulConstraints = listOf(StatefulConstraintSummary("manual-judgement", OVERRIDE_PASS, startedAt = Instant.parse("2020-03-25T00:00:00Z"), judgedBy = "lpollo@acme.com", judgedAt = Instant.parse("2020-03-25T01:30:00Z"), comment = "Aye!"), StatefulConstraintSummary("pipeline", NOT_EVALUATED)),
              statelessConstraints = listOf(StatelessConstraintSummary("depends-on", true, DependOnConstraintMetadata("staging"))))
          ),
          build = BuildMetadata(0),
          git = GitMetadata("a0a0a0a")
        )

        expect {
          that(summaries.size).isEqualTo(1)
          that(summaries.first().versions.find { it.version == version4 }).isEqualTo(v4)
          that(summaries.first().versions.find { it.version == version3 }).isEqualTo(v3)
          that(summaries.first().versions.find { it.version == version2 }).isEqualTo(v2)
          that(summaries.first().versions.find { it.version == version1 }).isEqualTo(v1)
          that(summaries.first().versions.find { it.version == version0 }).isEqualTo(v0)
        }
      }

      test("no constraints have been evaluated") {
        val states = applicationService.getConstraintStatesFor(application, "prod", 10)
        expectThat(states).isEmpty()
      }

      test("pending manual judgement") {
        val judgement = ConstraintState(deliveryConfig.name, "production", version2, "manual-judgement", PENDING)
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
        val judgement = ConstraintState(deliveryConfig.name, "production", version2, "manual-judgement", PENDING)
        repository.storeConstraintState(judgement)

        val updatedState = UpdatedConstraintStatus("manual-judgement", version2, OVERRIDE_PASS)
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
