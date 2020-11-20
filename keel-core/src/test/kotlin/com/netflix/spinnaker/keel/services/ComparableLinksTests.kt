package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
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
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.DummyArtifact
import com.netflix.spinnaker.keel.test.DummySortingStrategy
import com.netflix.spinnaker.keel.test.artifactReferenceResource
import com.netflix.spinnaker.keel.test.versionedArtifactResource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import strikt.api.Assertion
import strikt.api.DescribeableBuilder
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.isEqualTo
import java.time.Instant
import java.time.ZoneId

class ComparableLinksTests : JUnit5Minutests {

  class Fixture {
    val clock: MutableClock = MutableClock(
      Instant.parse("2020-03-25T00:00:00.00Z"),
      ZoneId.of("UTC")
    )
    val repository: KeelRepository = mockk()
    val resourceStatusService: ResourceStatusService = mockk()

    val application1 = "fnord1"

    val releaseArtifact = DummyArtifact(reference = "release")
    //val snapshotArtifact = DummyArtifact(reference = "snapshot")

    val version0 = "fnord-1.0.0-h0.a0a0a0a"
    val version1 = "fnord-1.0.1-h1.b1b1b1b"
    val version2 = "fnord-1.0.2-h2.c2c2c2c"
    val version3 = "fnord-1.0.3-h3.d3d3d3d"
    val version4 = "fnord-1.0.4-h4.e4e4e4e"

    val versions = listOf(version0, version1, version2, version3, version4)

    val singleArtifactEnvironments = listOf("test", "staging").associateWith { name ->
      Environment(
        name = name,
        constraints = emptySet(),
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

    private val artifactInstance = slot<PublishedArtifact>()
    private val artifactSupplier = mockk<ArtifactSupplier<DummyArtifact, DummySortingStrategy>>(relaxUnitFun = true) {
      every { supportedArtifact } returns SupportedArtifact("dummy", DummyArtifact::class.java)
      every {
        getVersionDisplayName(capture(artifactInstance))
      } answers {
        artifactInstance.captured.version
      }
      every { parseDefaultBuildMetadata(any(), any()) } returns null
      every { parseDefaultGitMetadata(any(), any()) } returns null
    }

    private val scmInfo = mockk<ScmInfo>() {
      coEvery {
        getScmInfo()
      } answers {
        mapOf("stash" to "https://stash")
      }
    }

    val dependsOnEvaluator = mockk<ConstraintEvaluator<DependsOnConstraint>>() {
      every { isImplicit() } returns false
      every { supportedType } returns SupportedConstraintType<DependsOnConstraint>("depends-on")
    }

    // subject
    val applicationService = ApplicationService(
      repository,
      resourceStatusService,
      listOf(dependsOnEvaluator),
      listOf(artifactSupplier),
      scmInfo
    )

    val buildMetadata = BuildMetadata(
      id = 1,
      number = "1",
    )

    val gitMetadata = GitMetadata(
      author = "keel user",
      commit = "1sdla",
      commitInfo = Commit(
        sha = "12345",
        link = "https://stash"
      ),
      repo = Repo(
        name = "keel"
      ),
      project = "spkr"
    )

    fun Collection<String>.toArtifactVersions(artifact: DeliveryArtifact) =
      map { PublishedArtifact(artifact.name, artifact.type, it) }
  }

  fun comparableLinksTests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    before {
      every { repository.getDeliveryConfigForApplication(application1) } returns singleArtifactDeliveryConfig

      every {
        repository.getArtifactVersion(any(), any(), any())
      } answers {
        PublishedArtifact(arg<DeliveryArtifact>(0).name, arg<DeliveryArtifact>(0).type, arg<String>(1))
      }

      every {
        repository.getReleaseStatus(releaseArtifact, any())
      } returns ArtifactStatus.RELEASE

      every {
        repository.getArtifactVersionByPromotionStatus(any(), any(), any(), any())
      } returns null

      every {
        repository.getPinnedVersion(any(), any(), any())
      } returns null
    }

    context("each environment has a current version, and previous versions") {
      before {
        every {
          repository.getEnvironmentSummaries(singleArtifactDeliveryConfig)
        } returns singleArtifactDeliveryConfig.environments.map { env ->
          toEnvironmentSummary(env) {
            when (env.name) {
              "test" -> ArtifactVersionStatus(
                previous = listOf(version0, version1),
                current = version2,
                deploying = version3,
                pending = listOf(version4)
              )
              "staging" -> ArtifactVersionStatus(
                previous = listOf(version0),
                current = version1,
                pending = listOf(version2)
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
              version0 -> ArtifactSummaryInEnvironment(environment, version, "previous", replacedBy = version1)
              version1 -> ArtifactSummaryInEnvironment(environment, version, "previous", replacedBy = version2)
              version2 -> ArtifactSummaryInEnvironment(environment, version, "current")
              version3 -> ArtifactSummaryInEnvironment(environment, version, "deploying")
              else -> ArtifactSummaryInEnvironment(environment, version, "pending")
            }
            "staging" -> when (val version = arg<String>(3)) {
              version0 -> ArtifactSummaryInEnvironment(environment, version, "previous", replacedBy = version1)
              version1 -> ArtifactSummaryInEnvironment(environment, version, "current")
              else -> ArtifactSummaryInEnvironment(environment, version, "pending")
            }
            else -> null
          }
        }

        every {
          repository.constraintStateFor(singleArtifactDeliveryConfig.name, any(), any<String>())
        } answers {
          emptyList()
          }


        every { repository.artifactVersions(releaseArtifact) } returns versions.toArtifactVersions(releaseArtifact)

        every {
          repository.getArtifactVersion(any(), any(), any())
        } answers {
          PublishedArtifact(arg<DeliveryArtifact>(0).name, arg<DeliveryArtifact>(0).type, arg<String>(1),
            gitMetadata = GitMetadata(commit = arg<String>(1),
              commitInfo = Commit(sha = arg<String>(1), link = "stash"),
              repo = Repo(name = "keel"),
              project = "spkr"
            )
            , buildMetadata = buildMetadata)
        }

        every {
            repository.getArtifactVersionByPromotionStatus(singleArtifactDeliveryConfig, any(), releaseArtifact, PromotionStatus.PREVIOUS.name, any())
          } answers {
            PublishedArtifact(
              name = arg<DeliveryArtifact>(2).name,
              type = arg<DeliveryArtifact>(2).type,
              version = version0,
              gitMetadata = GitMetadata(commit = arg<String>(1),
                commitInfo = Commit(sha = "${arg<String>(1)}:$version0", link = "stash"))
            )
          }

        every {
          repository.getArtifactVersionByPromotionStatus(singleArtifactDeliveryConfig, any(), releaseArtifact, PromotionStatus.CURRENT.name)
        } answers {
          PublishedArtifact(
            name = arg<DeliveryArtifact>(2).name,
            type = arg<DeliveryArtifact>(2).type,
            version = version1,
            gitMetadata = GitMetadata(commit = arg<String>(1),
              commitInfo = Commit(sha = "${arg<String>(1)}:$version1", link = "stash"))
          )
        }

      }

      test("compare links for previous-->current are generated as expected, in the correct env") {
        val summaries = applicationService.getArtifactSummariesFor(application1)
        expectThat(summaries.first())
          .withVersionInEnvironment(version2, "test") {
            state.isEqualTo(PromotionStatus.CURRENT.name.toLowerCase())
            compareLink
              .isEqualTo("https://stash/projects/spkr/repos/keel/compare/commits?targetBranch=test:fnord-1.0.0-h0.a0a0a0a&sourceBranch=fnord-1.0.2-h2.c2c2c2c")
          }
          .withVersionInEnvironment(version1, "staging") {
            state.isEqualTo(PromotionStatus.CURRENT.name.toLowerCase())
            compareLink.isEqualTo("https://stash/projects/spkr/repos/keel/compare/commits?targetBranch=staging:fnord-1.0.0-h0.a0a0a0a&sourceBranch=fnord-1.0.1-h1.b1b1b1b")
          }
      }

      test("compare links for current --> deploying are generated as expected, in the correct env") {
        val summaries = applicationService.getArtifactSummariesFor(application1)
        expectThat(summaries.first())
          .withVersionInEnvironment(version3, "test") {
            state.isEqualTo(PromotionStatus.DEPLOYING.name.toLowerCase())
            compareLink
              .isEqualTo("https://stash/projects/spkr/repos/keel/compare/commits?targetBranch=test:fnord-1.0.1-h1.b1b1b1b&sourceBranch=fnord-1.0.3-h3.d3d3d3d")
          }
      }

      test("compare links for previous --> current  are generated as expected, in the correct env") {
        val summaries = applicationService.getArtifactSummariesFor(application1)
        expectThat(summaries.first())
          .withVersionInEnvironment(version0, "staging") {
            state.isEqualTo(PromotionStatus.PREVIOUS.name.toLowerCase())
            compareLink.isEqualTo("https://stash/projects/spkr/repos/keel/compare/commits?targetBranch=fnord-1.0.0-h0.a0a0a0a&sourceBranch=fnord-1.0.1-h1.b1b1b1b")
          }
      }

      test("compare links for pending --> current  are generated as expected, in the correct env") {
        val summaries = applicationService.getArtifactSummariesFor(application1)
        expectThat(summaries.first())
          .withVersionInEnvironment(version2, "staging") {
            state.isEqualTo(ConstraintStatus.PENDING.name.toLowerCase())
            compareLink.isEqualTo("https://stash/projects/spkr/repos/keel/compare/commits?targetBranch=staging:fnord-1.0.1-h1.b1b1b1b&sourceBranch=fnord-1.0.2-h2.c2c2c2c")
          }
      }

      context("pinned") {
        before {
          every {
            repository.getPinnedVersion(singleArtifactDeliveryConfig, "test", releaseArtifact.reference)
          } returns version0

          every {
            repository.getPinnedVersion(singleArtifactDeliveryConfig, "staging", releaseArtifact.reference)
          } returns version3

          every {
            repository.getArtifactVersionByPromotionStatus(singleArtifactDeliveryConfig, any(), releaseArtifact, PromotionStatus.PREVIOUS.name, version0)
          } answers {
            PublishedArtifact(
              name = arg<DeliveryArtifact>(2).name,
              type = arg<DeliveryArtifact>(2).type,
              version = version1,
              gitMetadata = GitMetadata(commit = arg<String>(1),
                commitInfo = Commit(sha = "pinnedVersion", link = "stash"),
                repo = Repo(name = "keel"),
                project = "spkr"
              )
            )
          }

          every {
            repository.getArtifactVersion(releaseArtifact, version3, ArtifactStatus.RELEASE)
          } answers {
            PublishedArtifact(
              name = arg<DeliveryArtifact>(0).name,
              type = arg<DeliveryArtifact>(0).type,
              version = arg<String>(1),
              gitMetadata = GitMetadata(
                commit = "pinnedVersion",
                commitInfo = Commit(sha = "pinnedVersion", link = "stash"),
                repo = Repo(name = "keel"),
                project = "spkr"
              ),
              buildMetadata = buildMetadata
            )
          }
          every {
            repository.getArtifactVersion(releaseArtifact, version0, ArtifactStatus.RELEASE)
          } answers {
            PublishedArtifact(
              name = arg<DeliveryArtifact>(0).name,
              type = arg<DeliveryArtifact>(0).type,
              version = arg<String>(1),
              gitMetadata = GitMetadata(
                commit = "pinnedVersion",
                commitInfo = Commit(sha = "pinnedVersion", link = "stash"),
                repo = Repo(name = "keel"),
                project = "spkr"
              ),
              buildMetadata = buildMetadata
            )
          }
        }
        test ("get the correct compare link when pinning forward (current)") {
          val summaries = applicationService.getArtifactSummariesFor(application1)
          expectThat(summaries.first())
            .withVersionInEnvironment(version1, "staging") {
              compareLink.isEqualTo("https://stash/projects/spkr/repos/keel/compare/commits?targetBranch=fnord-1.0.1-h1.b1b1b1b&sourceBranch=pinnedVersion")
            }
        }

        test ("get the correct compare link when pinning forward (pending)") {
          val summaries = applicationService.getArtifactSummariesFor(application1)
          expectThat(summaries.first())
            .withVersionInEnvironment(version4, "test") {
              compareLink.isEqualTo("https://stash/projects/spkr/repos/keel/compare/commits?targetBranch=pinnedVersion&sourceBranch=fnord-1.0.4-h4.e4e4e4e")
            }
        }

        test ("get the correct compare link when pinning forward (prev)") {
          val summaries = applicationService.getArtifactSummariesFor(application1)
          expectThat(summaries.first())
            .withVersionInEnvironment(version1, "test") {
              compareLink.isEqualTo("https://stash/projects/spkr/repos/keel/compare/commits?targetBranch=fnord-1.0.1-h1.b1b1b1b&sourceBranch=fnord-1.0.2-h2.c2c2c2c")
            }
        }

        test ("get the correct compare link when pinning backwards") {
          val summaries = applicationService.getArtifactSummariesFor(application1)
          expectThat(summaries.first())
            .withVersionInEnvironment(version2, "test") {
              compareLink.isEqualTo("https://stash/projects/spkr/repos/keel/compare/commits?targetBranch=pinnedVersion&sourceBranch=fnord-1.0.2-h2.c2c2c2c")
            }
        }

        context ("pin version == current version") {
          before {
            every {
              repository.getPinnedVersion(singleArtifactDeliveryConfig, "staging", releaseArtifact.reference)
            } returns version1
          }
          test("generate the right compare link") {
            val summaries = applicationService.getArtifactSummariesFor(application1)
            expectThat(summaries.first())
              .withVersionInEnvironment(version1, "staging") {
                compareLink.isEqualTo("https://stash/projects/spkr/repos/keel/compare/commits?targetBranch=staging:fnord-1.0.0-h0.a0a0a0a&sourceBranch=fnord-1.0.1-h1.b1b1b1b")
              }
          }
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

  val Assertion.Builder<ArtifactSummaryInEnvironment>.state: Assertion.Builder<String>
    get() = get { state }

  val Assertion.Builder<ArtifactSummaryInEnvironment>.compareLink: DescribeableBuilder<String?>
    get() = get { compareLink }
}
