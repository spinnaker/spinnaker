package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DEFAULT_MAX_ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.core.api.ArtifactSummary
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.ArtifactVersionStatus
import com.netflix.spinnaker.keel.core.api.ArtifactVersionSummary
import com.netflix.spinnaker.keel.core.api.ArtifactVersions
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.PipelineConstraint
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PREVIOUS
import com.netflix.spinnaker.keel.core.api.PromotionStatus.SKIPPED
import com.netflix.spinnaker.keel.core.api.PromotionStatus.VETOED
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CREATED
import com.netflix.spinnaker.keel.test.DummyArtifact
import com.netflix.spinnaker.keel.test.DummyVersioningStrategy
import com.netflix.spinnaker.keel.test.artifactReferenceResource
import com.netflix.spinnaker.keel.test.versionedArtifactResource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.ZoneId
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import strikt.assertions.withFirst

class ApplicationServiceTests : JUnit5Minutests {
  class Fixture {
    val clock: MutableClock = MutableClock(
      Instant.parse("2020-03-25T00:00:00.00Z"),
      ZoneId.of("UTC")
    )
    val repository: KeelRepository = mockk()
    val resourceStatusService: ResourceStatusService = mockk()

    val application1 = "fnord1"
    val application2 = "fnord2"

    val releaseArtifact = DummyArtifact(reference = "release")
    val snapshotArtifact = DummyArtifact(reference = "snapshot")

    val singleArtifactEnvironments = listOf("test", "staging", "production").associateWith { name ->
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
          // resource with new-style artifact reference
          artifactReferenceResource(artifactReference = "release"),
          // resource with old-style image provider
          versionedArtifactResource()
        )
      )
    }

    val singleArtifactDeliveryConfig = DeliveryConfig(
      name = "manifest_$application1",
      application = application1,
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(releaseArtifact),
      environments = singleArtifactEnvironments.values.toSet()
    )

    val dualArtifactEnvironments = listOf("pr", "test").associateWith { name ->
      Environment(
        name = name,
        constraints = emptySet(),
        resources = setOf(
          artifactReferenceResource(artifactReference = if (name == "pr") "snapshot" else "release")
        )
      )
    }

    val dualArtifactDeliveryConfig = DeliveryConfig(
      name = "manifest_$application2",
      application = application2,
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(releaseArtifact, snapshotArtifact),
      environments = dualArtifactEnvironments.values.toSet()
    )

    val version0 = "fnord-1.0.0-h0.a0a0a0a"
    val version1 = "fnord-1.0.1-h1.b1b1b1b"
    val version2 = "fnord-1.0.2-h2.c2c2c2c"
    val version3 = "fnord-1.0.3-h3.d3d3d3d"
    val version4 = "fnord-1.0.4-h4.e4e4e4e"
    val versions = listOf(version0, version1, version2, version3, version4)

    val snapshotVersion1 = "fnord-1.0.0~dev.1-h3.d3d3d3d"
    val snapshotVersion2 = "fnord-1.0.0~dev.2-h4.e4e4e4e"
    val snapshotVersions = listOf(snapshotVersion1, snapshotVersion2)

    val pin = EnvironmentArtifactPin("production", releaseArtifact.reference, version0, "keel@keel.io", "comment")

    val dependsOnEvaluator = mockk<ConstraintEvaluator<DependsOnConstraint>>() {
      every { isImplicit() } returns false
      every { supportedType } returns SupportedConstraintType<DependsOnConstraint>("depends-on")
    }

    private val artifactInstance = slot<PublishedArtifact>()
    private val artifactSupplier = mockk<ArtifactSupplier<DummyArtifact, DummyVersioningStrategy>>(relaxUnitFun = true) {
      every { supportedArtifact } returns SupportedArtifact("dummy", DummyArtifact::class.java)
      every {
        getVersionDisplayName(capture(artifactInstance))
      } answers {
        artifactInstance.captured.version
      }
      every { parseDefaultBuildMetadata(any(), any()) } returns null
      every { parseDefaultGitMetadata(any(), any()) } returns null
    }

    // subject
    val applicationService = ApplicationService(
      repository,
      resourceStatusService,
      listOf(dependsOnEvaluator),
      listOf(artifactSupplier)
    )

    val buildMetadata = BuildMetadata(
      id = 1,
      number = "1",
    )

    val gitMetadata = GitMetadata (
      author = "keel user",
      commit = "1sdla"
    )
  }

  fun applicationServiceTests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    before {
      every { repository.getDeliveryConfigForApplication(application1) } returns singleArtifactDeliveryConfig

      every { repository.getDeliveryConfigForApplication(application2) } returns dualArtifactDeliveryConfig

      every {
        repository.getArtifactInstance(any(), any(), any(), any())
      } answers {
        PublishedArtifact(arg<String>(0), arg<String>(1), arg<String>(2))
      }

      every {
        repository.getReleaseStatus(releaseArtifact, any())
      } returns RELEASE

      every {
        repository.getReleaseStatus(snapshotArtifact, any())
      } returns SNAPSHOT
    }

    context("artifact summaries by application") {
      before {
        every { repository.artifactVersions(releaseArtifact) } returns versions
        every { repository.artifactVersions(snapshotArtifact) } returns snapshotVersions
      }

      context("a delivery config with a single artifact for all environments") {
        before {
          every {
            repository.constraintStateFor(singleArtifactDeliveryConfig.name, any(), any<String>())
          } returns emptyList()

          every {
            repository.getArtifactInstance(any(), any(), any(), any())
          } answers {
            PublishedArtifact(arg<String>(0), arg<String>(1), arg<String>(2), gitMetadata = gitMetadata, buildMetadata = buildMetadata)
          }
        }

        context("all versions are pending in all environments") {
          before {
            every {
              repository.getEnvironmentSummaries(singleArtifactDeliveryConfig)
            } returns singleArtifactDeliveryConfig.environments.map { env ->
              toEnvironmentSummary(env) {
                ArtifactVersionStatus(
                  pending = versions
                )
              }
            }

            every {
              dependsOnEvaluator.canPromote(releaseArtifact, any(), singleArtifactDeliveryConfig, singleArtifactEnvironments.getValue("production"))
            } returns false
          }

          test("artifact summary shows all versions pending in all environments") {
            val summaries = applicationService.getArtifactSummariesFor(application1)

            expectThat(summaries) {
              hasSize(1)
              withFirst {
                get { name }.isEqualTo(releaseArtifact.name)
                with(ArtifactSummary::versions) {
                  hasSize(versions.size)
                  all {
                    with(ArtifactVersionSummary::environments) {
                      hasSize(singleArtifactEnvironments.size)
                      all {
                        state.isEqualTo(PENDING.name.toLowerCase())
                      }
                    }
                  }
                    .first().and {
                      get { build }.isEqualTo(buildMetadata)
                      get { git }.isEqualTo(gitMetadata)
                    }
                }
              }
            }
          }
        }

        context("each environment has a current version, and previous versions") {
          before {
            every {
              repository.getEnvironmentSummaries(singleArtifactDeliveryConfig)
            } returns singleArtifactDeliveryConfig.environments.map { env ->
              toEnvironmentSummary(env) {
                when (env.name) {
                  "test" -> ArtifactVersionStatus(
                    previous = listOf(version0, version1, version2),
                    current = version3,
                    pending = listOf(version4)
                  )
                  "staging" -> ArtifactVersionStatus(
                    previous = listOf(version0, version1),
                    current = version2,
                    pending = listOf(version3, version4)
                  )
                  "production" -> ArtifactVersionStatus(
                    previous = listOf(version0),
                    current = version1,
                    pending = listOf(version2, version3, version4)
                  )
                  else -> error("Unexpected environment ${env.name}")
                }
              }
            }

            // for statuses other than PENDING, we go look for the artifact summary in environment
            every {
              repository.getArtifactSummaryInEnvironment(singleArtifactDeliveryConfig, any(), any(), any())
            } answers {
              when (val environment = arg<String>(1)) {
                "test" -> when (val version = arg<String>(3)) {
                  version0,
                  version1,
                  version2 -> ArtifactSummaryInEnvironment(environment, version, "previous")
                  version3 -> ArtifactSummaryInEnvironment(environment, version, "current")
                  else -> ArtifactSummaryInEnvironment(environment, version, "pending")
                }
                "staging" -> when (val version = arg<String>(3)) {
                  version0,
                  version1 -> ArtifactSummaryInEnvironment(environment, version, "previous")
                  version2 -> ArtifactSummaryInEnvironment(environment, version, "current")
                  else -> ArtifactSummaryInEnvironment(environment, version, "pending")
                }
                "production" -> when (val version = arg<String>(3)) {
                  version0 -> ArtifactSummaryInEnvironment(environment, version, "previous")
                  version1 -> ArtifactSummaryInEnvironment(environment, version, "current")
                  else -> ArtifactSummaryInEnvironment(environment, version, "pending")
                }
                else -> null
              }
            }

            every {
              repository.constraintStateFor(singleArtifactDeliveryConfig.name, any(), any<String>())
            } answers {
              when (val environment = arg<String>(1)) {
                "production" -> {
                  val version = arg<String>(2)
                  val type = "deb"
                  listOf(
                    ConstraintState(
                      singleArtifactDeliveryConfig.name,
                      environment,
                      version,
                      type,
                      "pipeline",
                      if (version in listOf(version0, version1)) PASS else PENDING
                    )
                  )
                }
                else -> emptyList()
              }
            }

            every {
              dependsOnEvaluator.canPromote(releaseArtifact, any(), singleArtifactDeliveryConfig, singleArtifactEnvironments.getValue("production"))
            } answers {
              arg(1) in listOf(version0, version1)
            }
          }

          test("artifact summary shows correct current version in each environment") {
            val summaries = applicationService.getArtifactSummariesFor(application1)
            expectThat(summaries) {
              first()
                .withVersionInEnvironment(version3, "test") {
                  state.isEqualTo(CURRENT.name.toLowerCase())
                }
                .withVersionInEnvironment(version2, "staging") {
                  state.isEqualTo(CURRENT.name.toLowerCase())
                }
                .withVersionInEnvironment(version1, "production") {
                  state.isEqualTo(CURRENT.name.toLowerCase())
                }
            }
          }

          test("artifact summary shows correct previous versions in each environment") {
            val summaries = applicationService.getArtifactSummariesFor(application1)
            expectThat(summaries) {
              first()
                .withVersionInEnvironment(version2, "test") {
                  state.isEqualTo(PREVIOUS.name.toLowerCase())
                }
                .withVersionInEnvironment(version1, "test") {
                  state.isEqualTo(PREVIOUS.name.toLowerCase())
                }
                .withVersionInEnvironment(version0, "test") {
                  state.isEqualTo(PREVIOUS.name.toLowerCase())
                }
                .withVersionInEnvironment(version1, "staging") {
                  state.isEqualTo(PREVIOUS.name.toLowerCase())
                }
                .withVersionInEnvironment(version0, "staging") {
                  state.isEqualTo(PREVIOUS.name.toLowerCase())
                }
                .withVersionInEnvironment(version0, "production") {
                  state.isEqualTo(PREVIOUS.name.toLowerCase())
                }
            }
          }

          test("artifact summary shows correct pending versions in each environment") {
            val summaries = applicationService.getArtifactSummariesFor(application1)
            expectThat(summaries) {
              first()
                .withVersionInEnvironment(version4, "test") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
                .withVersionInEnvironment(version4, "staging") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
                .withVersionInEnvironment(version3, "staging") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
                .withVersionInEnvironment(version4, "production") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
                .withVersionInEnvironment(version3, "production") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
                .withVersionInEnvironment(version2, "production") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
            }
          }

          test("stateless constraint details are included in the summary") {
            val summaries = applicationService.getArtifactSummariesFor(application1)

            expectThat(summaries.first())
              .withVersionInEnvironment(version1, "production") {
                get { statelessConstraints }
                  .hasSize(1)
                  .first().and {
                    get { type }.isEqualTo("depends-on")
                    get { currentlyPassing }.isTrue()
                  }
              }
              .withVersionInEnvironment(version2, "production") {
                get { statelessConstraints }
                  .hasSize(1)
                  .first().and {
                    get { type }.isEqualTo("depends-on")
                    get { currentlyPassing }.isFalse()
                  }
              }
          }

          test("stateful constraint details are included in the summary") {
            val summaries = applicationService.getArtifactSummariesFor(application1)

            expectThat(summaries.first())
              .withVersionInEnvironment(version1, "production") {
                get { statefulConstraints }
                  .first { it.type == "pipeline" }
                  .get { status }.isEqualTo(PASS)
              }
              .withVersionInEnvironment(version2, "production") {
                get { statefulConstraints }
                  .first { it.type == "pipeline" }
                  .get { status }.isEqualTo(PENDING)
              }
          }

          test("non-evaluated stateful constraint details are included in the summary") {
            val summaries = applicationService.getArtifactSummariesFor(application1)

            expectThat(summaries.first { it.reference == releaseArtifact.reference })
              .withVersionInEnvironment(version1, "production") {
                get { statefulConstraints }
                  .first { it.type == "manual-judgement" }
                  .get { status }.isEqualTo(NOT_EVALUATED)
              }
          }
        }

        context("there is a skipped version in an environment") {
          before {
            every {
              repository.getEnvironmentSummaries(singleArtifactDeliveryConfig)
            } returns singleArtifactDeliveryConfig.environments.map { env ->
              toEnvironmentSummary(env) {
                when (env.name) {
                  "test" -> ArtifactVersionStatus(
                    previous = listOf(version0),
                    skipped = listOf(version1),
                    current = version2,
                    pending = listOf(version3, version4)
                  )
                  else -> ArtifactVersionStatus(
                    pending = versions
                  )
                }
              }
            }

            every {
              repository.constraintStateFor(singleArtifactDeliveryConfig.name, any(), any<String>())
            } returns emptyList()

            every {
              dependsOnEvaluator.canPromote(releaseArtifact, any(), singleArtifactDeliveryConfig, singleArtifactEnvironments.getValue("production"))
            } returns false
          }

          context("the skipped version has an artifact summary for the environment with a status other than 'pending'") {
            before {
              // for statuses other than PENDING, we go look for the artifact summary in environment
              every {
                repository.getArtifactSummaryInEnvironment(singleArtifactDeliveryConfig, any(), any(), any())
              } answers {
                val environment = arg<String>(1)
                when (val version = arg<String>(3)) {
                  version2 -> ArtifactSummaryInEnvironment(environment, version, "current")
                  version1 -> ArtifactSummaryInEnvironment(environment, version, "vetoed")
                  version0 -> ArtifactSummaryInEnvironment(environment, version, "previous")
                  else -> ArtifactSummaryInEnvironment(environment, version, "pending")
                }
              }
            }

            test("we use the summary we found for the skipped version") {
              val summaries = applicationService.getArtifactSummariesFor(application1)

              expectThat(summaries) {
                first()
                  .withVersionInEnvironment(version1, "test") {
                    state.isEqualTo(VETOED.name.toLowerCase())
                  }
              }
            }
          }

          context("the skipped version has an artifact summary for the environment with a status of 'pending'") {
            before {
              // for statuses other than PENDING, we go look for the artifact summary in environment
              every {
                repository.getArtifactSummaryInEnvironment(singleArtifactDeliveryConfig, any(), any(), any())
              } answers {
                val environment = arg<String>(1)
                when (val version = arg<String>(3)) {
                  version2 -> ArtifactSummaryInEnvironment(environment, version, "current")
                  version1 -> ArtifactSummaryInEnvironment(environment, version, "pending")
                  version0 -> ArtifactSummaryInEnvironment(environment, version, "previous")
                  else -> ArtifactSummaryInEnvironment(environment, version, "pending")
                }
              }
            }

            test("we ignore the summary we found for the skipped version") {
              val summaries = applicationService.getArtifactSummariesFor(application1)

              expectThat(summaries) {
                first()
                  .withVersionInEnvironment(version1, "test") {
                    state.isEqualTo(SKIPPED.name.toLowerCase())
                  }
              }
            }
          }

          context("the skipped version has no artifact summary for the environment") {
            before {
              // for statuses other than PENDING, we go look for the artifact summary in environment
              every {
                repository.getArtifactSummaryInEnvironment(singleArtifactDeliveryConfig, any(), any(), any())
              } answers {
                val environment = arg<String>(1)
                when (val version = arg<String>(3)) {
                  version2 -> ArtifactSummaryInEnvironment(environment, version, "current")
                  version1 -> null
                  version0 -> ArtifactSummaryInEnvironment(environment, version, "previous")
                  else -> ArtifactSummaryInEnvironment(environment, version, "pending")
                }
              }
            }

            test("we synthesize a summary for the skipped version") {
              val summaries = applicationService.getArtifactSummariesFor(application1)

              expectThat(summaries) {
                first()
                  .withVersionInEnvironment(version1, "test") {
                    state.isEqualTo(SKIPPED.name.toLowerCase())
                  }
              }
            }
          }
        }

        context("the artifact has lots of versions") {
          before {
            val lotsaVersions = (1..100).map { "1.0.$it" }
            val maxArtifactVersions = slot<Int>()

            every {
              repository.artifactVersions(releaseArtifact, capture(maxArtifactVersions))
            } answers {
              lotsaVersions.subList(0, maxArtifactVersions.captured)
            }

            every {
              repository.getEnvironmentSummaries(singleArtifactDeliveryConfig)
            } returns singleArtifactDeliveryConfig.environments.map { env ->
              toEnvironmentSummary(env) {
                ArtifactVersionStatus(
                  pending = lotsaVersions
                )
              }
            }

            every {
              dependsOnEvaluator.canPromote(releaseArtifact, any(), singleArtifactDeliveryConfig, singleArtifactEnvironments.getValue("production"))
            } returns false
          }

          test("getting artifact summaries has a default cap on versions") {
            applicationService.getArtifactSummariesFor(application1).also {
              verify { repository.artifactVersions(releaseArtifact, DEFAULT_MAX_ARTIFACT_VERSIONS) }
              expectThat(it.first().versions).hasSize(DEFAULT_MAX_ARTIFACT_VERSIONS)
            }
          }

          test("getting artifact summaries respects the max versions parameter") {
            applicationService.getArtifactSummariesFor(application1, 20).also {
              verify { repository.artifactVersions(releaseArtifact, 20) }
              expectThat(it.first().versions).hasSize(20)
            }

            applicationService.getArtifactSummariesFor(application1, 100).also {
              verify { repository.artifactVersions(releaseArtifact, 100) }
              expectThat(it.first().versions).hasSize(100)
            }
          }
        }
      }

      context("a delivery config with a release artifact and a snapshot artifact in different environments") {
        context("each environment has a current version, and previous versions") {
          before {
            every {
              repository.getEnvironmentSummaries(dualArtifactDeliveryConfig)
            } returns dualArtifactDeliveryConfig.environments.map { env ->
              when (env.name) {
                "pr" -> EnvironmentSummary(env,
                  setOf(
                    ArtifactVersions(
                      name = releaseArtifact.name,
                      type = releaseArtifact.type,
                      reference = releaseArtifact.reference,
                      statuses = emptySet(),
                      versions = ArtifactVersionStatus(
                        previous = emptyList(),
                        current = null,
                        pending = listOf(version0, version1, version2, version3, version4)
                      ),
                      pinnedVersion = null
                    ),
                    ArtifactVersions(
                      name = snapshotArtifact.name,
                      type = snapshotArtifact.type,
                      reference = snapshotArtifact.reference,
                      statuses = emptySet(),
                      versions = ArtifactVersionStatus(
                        previous = listOf(snapshotVersion1),
                        current = snapshotVersion2,
                        pending = emptyList()
                      ),
                      pinnedVersion = null
                    )
                  )
                )
                "test" -> EnvironmentSummary(env,
                  setOf(
                    ArtifactVersions(
                      name = releaseArtifact.name,
                      type = releaseArtifact.type,
                      reference = releaseArtifact.reference,
                      statuses = emptySet(),
                      versions = ArtifactVersionStatus(
                        previous = listOf(version0, version1, version2),
                        current = version3,
                        pending = listOf(version4)
                      ),
                      pinnedVersion = null
                    ),
                    ArtifactVersions(
                      name = snapshotArtifact.name,
                      type = snapshotArtifact.type,
                      reference = snapshotArtifact.reference,
                      statuses = emptySet(),
                      versions = ArtifactVersionStatus(
                        previous = emptyList(),
                        current = null,
                        pending = listOf(snapshotVersion1, snapshotVersion2)
                      ),
                      pinnedVersion = null
                    )
                  )
                )
                else -> error("Unexpected environment ${env.name}")
              }
            }

            // for statuses other than PENDING, we go look for the artifact summary in environment
            every {
              repository.getArtifactSummaryInEnvironment(dualArtifactDeliveryConfig, any(), any(), any())
            } answers {
              when (val environment = arg<String>(1)) {
                "pr" -> when (val version = arg<String>(3)) {
                  snapshotVersion1 -> ArtifactSummaryInEnvironment(environment, version, "previous")
                  snapshotVersion2 -> ArtifactSummaryInEnvironment(environment, version, "current")
                  else -> ArtifactSummaryInEnvironment(environment, version, "pending")
                }
                "test" -> when (val version = arg<String>(3)) {
                  version0,
                  version1,
                  version2 -> ArtifactSummaryInEnvironment(environment, version, "previous")
                  version3 -> ArtifactSummaryInEnvironment(environment, version, "current")
                  else -> ArtifactSummaryInEnvironment(environment, version, "pending")
                }
                else -> null
              }
            }

            every {
              repository.constraintStateFor(dualArtifactDeliveryConfig.name, any(), any<String>())
            } returns emptyList()
          }

          test("artifact summary shows correct current version in each environment") {
            val summaries = applicationService.getArtifactSummariesFor(application2)
            expectThat(summaries) {
              hasSize(2)
              first { it.reference == snapshotArtifact.reference }
                .withVersionInEnvironment(snapshotVersion2, "pr") {
                  state.isEqualTo(CURRENT.name.toLowerCase())
                }
              first { it.reference == releaseArtifact.reference }
                .withVersionInEnvironment(version3, "test") {
                  state.isEqualTo(CURRENT.name.toLowerCase())
                }
            }
          }

          test("artifact summary shows correct previous versions in each environment") {
            val summaries = applicationService.getArtifactSummariesFor(application2)
            expectThat(summaries) {
              first { it.reference == snapshotArtifact.reference }
                .withVersionInEnvironment(snapshotVersion1, "pr") {
                  state.isEqualTo(PREVIOUS.name.toLowerCase())
                }
              first { it.reference == releaseArtifact.reference }
                .withVersionInEnvironment(version2, "test") {
                  state.isEqualTo(PREVIOUS.name.toLowerCase())
                }
                .withVersionInEnvironment(version1, "test") {
                  state.isEqualTo(PREVIOUS.name.toLowerCase())
                }
                .withVersionInEnvironment(version0, "test") {
                  state.isEqualTo(PREVIOUS.name.toLowerCase())
                }
            }
          }

          test("artifact summary shows correct pending versions in each environment") {
            val summaries = applicationService.getArtifactSummariesFor(application2)
            expectThat(summaries) {
              first { it.reference == releaseArtifact.reference }
                .withVersionInEnvironment(version4, "pr") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
                .withVersionInEnvironment(version3, "pr") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
                .withVersionInEnvironment(version2, "pr") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
                .withVersionInEnvironment(version1, "pr") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
                .withVersionInEnvironment(version0, "pr") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
              first { it.reference == snapshotArtifact.reference }
                .withVersionInEnvironment(snapshotVersion2, "test") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
                .withVersionInEnvironment(snapshotVersion1, "test") {
                  state.isEqualTo(PENDING.name.toLowerCase())
                }
            }
          }
        }
      }
    }

    context("resource summaries by application") {
      before {
        every { resourceStatusService.getStatus(any()) } returns CREATED
      }

      test("includes all resources within the delivery config") {
        val summaries = applicationService.getResourceSummariesFor(application1)
        expectThat(summaries.size).isEqualTo(singleArtifactDeliveryConfig.resources.size)
      }

      test("sets the resource status as returned by ResourceStatusService") {
        val summaries = applicationService.getResourceSummariesFor(application1)
        expectThat(summaries.map { it.status }.all { it == CREATED }).isTrue()
      }
    }

    context("pinning an artifact version in an environment") {
      before {
        every {
          repository.pinEnvironment(singleArtifactDeliveryConfig, pin)
        } just Runs
        applicationService.pin("keel@keel.io", application1, pin)
      }

      test("causes the pin to be persisted") {
        verify(exactly = 1) {
          repository.pinEnvironment(singleArtifactDeliveryConfig, pin)
        }
      }
    }

    context("unpinning a specific artifact in an environment") {
      before {
        every {
          repository.deletePin(singleArtifactDeliveryConfig, "production", releaseArtifact.reference)
        } just Runs
        applicationService.deletePin("keel@keel.io", application1, "production", releaseArtifact.reference)
      }

      test("causes the pin to be deleted") {
        verify(exactly = 1) {
          repository.deletePin(singleArtifactDeliveryConfig, "production", releaseArtifact.reference)
        }
      }
    }

    context("unpinning all artifacts in an environment") {
      before {
        every {
          repository.deletePin(singleArtifactDeliveryConfig, "production")
        } just Runs
        applicationService.deletePin("keel@keel.io", application1, "production")
      }

      test("causes all pins in the environment to be deleted") {
        verify(exactly = 1) {
          repository.deletePin(singleArtifactDeliveryConfig, "production")
        }
      }
    }
  }

  private fun Assertion.Builder<ArtifactSummary>.withVersionInEnvironment(
    version: String,
    environment: String,
    block: Assertion.Builder<ArtifactSummaryInEnvironment>.() -> Unit
  ): Assertion.Builder<ArtifactSummary> =
    with(ArtifactSummary::versions) {
      first { it.version == version }
        .with(ArtifactVersionSummary::environments) {
          first { it.environment == environment }
            .and(block)
        }
    }

  val Assertion.Builder<ArtifactSummaryInEnvironment>.state: Assertion.Builder<String>
    get() = get { state }

  private fun Fixture.toEnvironmentSummary(env: Environment, block: () -> ArtifactVersionStatus): EnvironmentSummary {
    return EnvironmentSummary(
      env,
      setOf(
        ArtifactVersions(
          name = releaseArtifact.name,
          type = releaseArtifact.type,
          reference = releaseArtifact.reference,
          statuses = emptySet(),
          versions = block(),
          pinnedVersion = null
        )
      )
    )
  }
}
