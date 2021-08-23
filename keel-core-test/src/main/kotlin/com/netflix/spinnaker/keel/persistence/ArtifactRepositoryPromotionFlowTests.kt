package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.api.artifacts.BranchFilter
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.Job
import com.netflix.spinnaker.keel.api.artifacts.PullRequest
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.ActionMetadata
import com.netflix.spinnaker.keel.core.api.ArtifactVersionStatus
import com.netflix.spinnaker.keel.core.api.ArtifactVersionVetoData
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.DEPLOYING
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PENDING
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PREVIOUS
import com.netflix.spinnaker.keel.core.api.PromotionStatus.SKIPPED
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expect
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isSuccess
import strikt.assertions.isTrue
import java.time.Clock
import java.time.Duration

/**
 * All the promotion flow tests around artifacts
 */
abstract class ArtifactRepositoryPromotionFlowTests<T : ArtifactRepository> : JUnit5Minutests {
  val publisher: ApplicationEventPublisher = mockk(relaxed = true)

  abstract fun factory(clock: Clock, publisher: ApplicationEventPublisher): T

  val clock = MutableClock()

  open fun T.flush() {}

  data class Fixture<T : ArtifactRepository>(
    val subject: T
  ) {
    // the artifact built off a feature branch
    val versionedSnapshotDebian = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "candidate",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      statuses = setOf(SNAPSHOT)
    )

    // the artifact built off of master
    val versionedReleaseDebian = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "master",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      statuses = setOf(RELEASE)
    )

    val versionedDockerArtifact = DockerArtifact(
      name = "docker",
      deliveryConfigName = "my-manifest",
      reference = "docker-artifact",
      tagVersionStrategy = BRANCH_JOB_COMMIT_BY_JOB
    )

    val debianFilteredByBranch = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "filteredByBranch",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilter(
        branch = BranchFilter(
          name = "my-feature-branch"
        )
      )
    )

    val debianFilteredByBranchPrefix = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "filteredByBranchPrefix",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilter(
        branch = BranchFilter(
          startsWith = "feature/"
        )
      )
    )

    val debianFilteredByBranchPattern = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "filteredByBranchPattern",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilter(
        branch = BranchFilter(
          regex = ".*feature.*"
        )
      )
    )

    val debianFilteredByPullRequest = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "filteredByPullRequest",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilter(
        pullRequestOnly = true
      )
    )

    val debianFilteredByPullRequestAndBranch = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "filteredByPullRequestAndBranch",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilter(
        branch = BranchFilter(
          name = "my-feature-branch"
        ),
        pullRequestOnly = true
      )
    )

    val testEnvironment = Environment("test")
    val stagingEnvironment = Environment("staging")
    val previewEnvironment1 = Environment("test-preview-branch1", isPreview = true)
      .addMetadata("branch" to "feature/preview-branch1")
    val previewEnvironment2 = Environment("test-preview-branch2", isPreview = true)
      .addMetadata("branch" to "feature/preview-branch2")
    val manifest = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(
        versionedSnapshotDebian,
        versionedReleaseDebian,
        versionedDockerArtifact,
        debianFilteredByBranch,
        debianFilteredByBranchPattern,
        debianFilteredByPullRequest,
        debianFilteredByPullRequestAndBranch,
        debianFilteredByBranchPrefix
      ),
      environments = setOf(testEnvironment, stagingEnvironment, previewEnvironment1, previewEnvironment2)
    )
    val version1 = "keeldemo-0.0.1~dev.8-h8.41595c4" // snapshot
    val version2 = "keeldemo-0.0.1~dev.9-h9.3d2c8ff" // snapshot
    val version3 = "keeldemo-0.0.1~dev.10-h10.1d2d542" // snapshot
    val version4 = "keeldemo-1.0.0-h11.518aea2" // release
    val version5 = "keeldemo-1.0.0-h12.4ea8a9d" // release
    val version6 = "master-h12.4ea8a9d"
    val version7 = "keeldemo-pull.7-h20.d0349c3" // pull request build
    val version8 = "keeldemo-pull.8-h21.27dc978" // pull request build

    val pin1 = EnvironmentArtifactPin(
      targetEnvironment = stagingEnvironment.name, // staging
      reference = versionedReleaseDebian.reference,
      version = version4, // the older release build
      pinnedBy = "keel@spinnaker",
      comment = "fnord"
    )

    val artifactMetadata = ArtifactMetadata(
      BuildMetadata(
        id = 1,
        uid = "1234",
        startedAt = "2020-11-24T04:44:04.000Z",
        completedAt = "2020-11-25T03:04:02.259Z",
        job = Job(
          name = "job bla bla",
          link = "enkins.com"
        ),
        number = "1"
      ),
      GitMetadata(
        commit = "a15p0",
        author = "keel-user",
        repo = Repo(
          name = "keel",
          link = ""
        ),
        pullRequest = PullRequest(
          number = "111",
          url = "www.github.com/pr/111"
        ),
        commitInfo = Commit(
          sha = "a15p0",
          message = "this is a commit message",
          link = ""
        ),
        project = "spkr",
      )
    )

    val limit = 15
  }

  open fun Fixture<T>.persist() {
    with(subject) {
      register(versionedSnapshotDebian)
      setOf(version1, version2, version3).forEach {
        storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(it, SNAPSHOT))
      }
      setOf(version4, version5).forEach {
        storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(it, RELEASE))
      }
      register(versionedReleaseDebian)
      setOf(version1, version2, version3).forEach {
        storeArtifactVersion(versionedReleaseDebian.toArtifactVersion(it, SNAPSHOT))
      }
      setOf(version4, version5).forEach {
        storeArtifactVersion(versionedReleaseDebian.toArtifactVersion(it, RELEASE))
      }
      register(versionedDockerArtifact)
      setOf(version6).forEach {
        storeArtifactVersion(versionedDockerArtifact.toArtifactVersion(it))
      }
      register(debianFilteredByBranch)
      register(debianFilteredByBranchPattern)
      register(debianFilteredByPullRequest)
      register(debianFilteredByPullRequestAndBranch)
      register(debianFilteredByBranchPrefix)
    }
    persist(manifest)
  }

  abstract fun persist(manifest: DeliveryConfig)

  private fun Fixture<T>.versionsIn(
    environment: Environment,
    artifact: DeliveryArtifact = versionedSnapshotDebian
  ): ArtifactVersionStatus {
    return subject
      .getEnvironmentSummaries(manifest)
      .first { it.name == environment.name }
      .artifacts
      .first {
        it.reference == artifact.reference
      }
      .versions
  }

  private fun Fixture<T>.storeArtifactVersionWithBranch(artifact: DeliveryArtifact, version: String, branch: String) =
    subject.storeArtifactVersion(
      artifact.toArtifactVersion(
        version = version,
        createdAt = clock.tickMinutes(10)
      ).copy(
        gitMetadata = artifactMetadata.gitMetadata?.copy(branch = branch),
        buildMetadata = artifactMetadata.buildMetadata
      )
    )

  /**
   * This function creates [versions.size] versions for artifact [debianFilteredByBranchPattern],
   * where only the first 15 of them have branch names that match the requested pattern
   */
  private fun Fixture<T>.storeVersionsForDebianFilteredByBranchPattern(versions: List<String>) {
    subject.register(debianFilteredByBranchPattern)
    versions.forEachIndexed { index, version ->
      storeArtifactVersionWithBranch(
        artifact = debianFilteredByBranchPattern,
        version = version,
        branch = when {
          index < 5 -> "my-feature-x"
          index < 10 -> "feature-branch-x"
          index < 15 -> "myfeature"
          else -> "a-non-matching-branch"
        }
      )
    }
  }

  /**
   * This function creates [versions.size] versions for artifact [debianFilteredByPullRequest],
   * where only the first 10 of them have the PR info
   */
  private fun Fixture<T>.storeVersionsForDebianFilteredByPullRequest(versions: List<String>) {
    subject.register(debianFilteredByPullRequest)
    versions.forEachIndexed { index, version ->
      subject.storeArtifactVersion(
        debianFilteredByPullRequest.toArtifactVersion(
          version = version,
          createdAt = clock.tickMinutes(10)
        ).copy(
          gitMetadata = artifactMetadata.gitMetadata!!.copy(
            // first 10 "versions" don't have pull request info
            pullRequest = if (index < 10) null else artifactMetadata.gitMetadata!!.pullRequest
          ),
          buildMetadata = artifactMetadata.buildMetadata
        )
      )
    }
  }

  fun tests() = rootContext<Fixture<T>> {
    fixture { Fixture(factory(clock, publisher)) }

    before {
      persist()
    }

    after {
      subject.flush()
    }

    context("no version has been promoted to an environment") {
      test("the approved version for that environment is null") {
        expectThat(subject.latestVersionApprovedIn(manifest, versionedSnapshotDebian, testEnvironment.name))
          .isNull()
      }

      test("versions are not considered successfully deployed") {
        setOf(version1, version2, version3).forEach {
          expectThat(subject.wasSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, it, testEnvironment.name))
            .isFalse()
        }
      }

      test("the artifact version is pending in the environment") {
        expectThat(versionsIn(testEnvironment)) {
          get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version1, version2, version3)
          get(ArtifactVersionStatus::current).isNull()
          get(ArtifactVersionStatus::deploying).isNull()
          get(ArtifactVersionStatus::previous).isEmpty()
        }

        expectThat(subject.isDeployingTo(manifest, testEnvironment.name)).isFalse()
      }

      context("pending versions") {
        test("can get pending versions") {
          expectThat(
            subject.getPendingVersionsInEnvironment(
              manifest,
              versionedSnapshotDebian.reference,
              testEnvironment.name
            ).size
          ).isEqualTo(3)
        }

        test("ignore versions if filtered by branch") {
          expectThat(
            subject.getPendingVersionsInEnvironment(
              manifest,
              debianFilteredByBranch.reference,
              testEnvironment.name
            ).size
          ).isEqualTo(0)
        }

        test("fetch only versions of the given branch") {
          subject.storeArtifactVersion(
            debianFilteredByBranch.toArtifactVersion(
              version = "version-with-branch",
              createdAt = clock.tickMinutes(10)
            ).copy(
              gitMetadata = artifactMetadata.gitMetadata?.copy(
                branch = debianFilteredByBranch.from!!.branch!!.name
              ),
              buildMetadata = artifactMetadata.buildMetadata
            )
          )
          expectThat(
            subject.getPendingVersionsInEnvironment(
              manifest,
              debianFilteredByBranch.reference,
              testEnvironment.name
            ).size
          ).isEqualTo(1)
        }

        test("fetch only versions with matching pattern") {
          storeVersionsForDebianFilteredByBranchPattern((20 downTo 1).map { "keeldemo-any-string-$it" })
          expectThat(
            subject.getPendingVersionsInEnvironment(
              manifest,
              debianFilteredByBranchPattern.reference,
              testEnvironment.name
            ).size
          ).isEqualTo(15)
        }

        test("fetch only versions from pull requests") {
          storeVersionsForDebianFilteredByPullRequest((20 downTo 1).map { "keeldemo-any-string-$it" })
          expectThat(
            subject.getPendingVersionsInEnvironment(
              manifest,
              debianFilteredByPullRequest.reference,
              testEnvironment.name
            ).size
          ).isEqualTo(10)
        }

        test("fetch only versions matching the preview environment branch") {
          subject.storeArtifactVersion(debianFilteredByBranchPrefix.toArtifactVersion(version7,
            gitMetadata = GitMetadata(commit = "sha1", branch = previewEnvironment1.branch))
          )

          subject.storeArtifactVersion(debianFilteredByBranchPrefix.toArtifactVersion(version8,
            gitMetadata = GitMetadata(commit = "sha2", branch = previewEnvironment2.branch))
          )

          val env1Versions = subject.getPendingVersionsInEnvironment(
            manifest,
            debianFilteredByBranchPrefix.reference,
            previewEnvironment1.name
          )

          val env2Versions = subject.getPendingVersionsInEnvironment(
            manifest,
            debianFilteredByBranchPrefix.reference,
            previewEnvironment2.name
          )

          expectThat(env1Versions.map { it.version })
            .containsExactly(version7)

          expectThat(env2Versions.map { it.version })
            .containsExactly(version8)

          expectThat(versionsIn(previewEnvironment1, debianFilteredByBranchPrefix)) {
            get(ArtifactVersionStatus::pending).containsExactly(version7)
          }

          expectThat(versionsIn(previewEnvironment2, debianFilteredByBranchPrefix)) {
            get(ArtifactVersionStatus::pending).containsExactly(version8)
          }
        }
      }

      test("an artifact version can be vetoed even if it was not previously deployed") {
        val veto = EnvironmentArtifactVeto(
          targetEnvironment = testEnvironment.name,
          reference = versionedSnapshotDebian.reference,
          version = version1,
          vetoedBy = "someone",
          comment = "testing if mark as bad works"
        )

        subject.markAsVetoedIn(deliveryConfig = manifest, veto = veto, force = true)

        expectThat(
          subject.vetoedEnvironmentVersions(manifest).map {
            it.copy(versions = it.versions.map { v -> v.copy(vetoedAt = null) }.toMutableSet())
          }
        )
          .isEqualTo(
            listOf(
              EnvironmentArtifactVetoes(
                deliveryConfigName = manifest.name,
                targetEnvironment = testEnvironment.name,
                artifact = versionedSnapshotDebian,
                versions = mutableSetOf(
                  ArtifactVersionVetoData(
                    version = veto.version,
                    vetoedBy = veto.vetoedBy,
                    vetoedAt = null,
                    comment = veto.comment
                  )
                )
              )
            )
          )
      }
    }

    context("another version is stuck in deploying") {
      before {
        subject.approveVersionFor(manifest, versionedSnapshotDebian, version1, testEnvironment.name)
        subject.markAsDeployingTo(manifest, versionedSnapshotDebian, version1, testEnvironment.name)
        subject.approveVersionFor(manifest, versionedSnapshotDebian, version2, testEnvironment.name)
        subject.markAsDeployingTo(manifest, versionedSnapshotDebian, version2, testEnvironment.name)
      }

      test("we update the status of the old version when we mark the new one deploying") {
        val v1summary = subject.getArtifactSummaryInEnvironment(
          manifest,
          testEnvironment.name,
          versionedSnapshotDebian.reference,
          version1
        )
        expectThat(v1summary)
          .isNotNull()
          .get { state }
          .isEqualTo("skipped")
      }
    }

    context("we mark old pending versions as skipped") {
      before {
        subject.approveVersionFor(manifest, versionedSnapshotDebian, version2, testEnvironment.name)
        subject.markAsSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, version2, testEnvironment.name)
      }

      test("we mark version 1 as skipped") {
        val artifactInEnvSummary =
          subject.getAllVersionsForEnvironment(versionedSnapshotDebian, manifest, testEnvironment.name)
        expectThat(artifactInEnvSummary.find { it.publishedArtifact.version == version1 }?.status)
          .isNotNull()
          .isEqualTo(SKIPPED)
      }
    }

    context("a version has been promoted to an environment") {
      before {
        clock.incrementBy(Duration.ofHours(1))
        subject.approveVersionFor(manifest, versionedSnapshotDebian, version1, testEnvironment.name)
        subject.markAsDeployingTo(manifest, versionedSnapshotDebian, version1, testEnvironment.name)
        subject.approveVersionFor(manifest, versionedDockerArtifact, version6, stagingEnvironment.name)
        subject.markAsDeployingTo(manifest, versionedDockerArtifact, version6, stagingEnvironment.name)
      }

      test("the approved version for that environment matches") {
        // debian
        expectThat(subject.latestVersionApprovedIn(manifest, versionedSnapshotDebian, testEnvironment.name))
          .isEqualTo(version1)
        // docker
        expectThat(subject.latestVersionApprovedIn(manifest, versionedDockerArtifact, stagingEnvironment.name))
          .isEqualTo(version6)
      }

      test("the version is not considered successfully deployed yet") {
        expectThat(subject.wasSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, version1, testEnvironment.name))
          .isFalse()
        expectThat(
          subject.wasSuccessfullyDeployedTo(
            manifest,
            versionedDockerArtifact,
            version6,
            stagingEnvironment.name
          )
        )
          .isFalse()
      }

      test("the version is deploying in the environment") {
        expectThat(versionsIn(testEnvironment)) {
          get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version2, version3)
          get(ArtifactVersionStatus::current).isNull()
          get(ArtifactVersionStatus::deploying).isEqualTo(version1)
          get(ArtifactVersionStatus::previous).isEmpty()
        }

        expectThat(subject.isDeployingTo(manifest, testEnvironment.name)).isTrue()

        expectThat(versionsIn(stagingEnvironment, versionedDockerArtifact)) {
          get(ArtifactVersionStatus::pending).isEmpty()
          get(ArtifactVersionStatus::current).isNull()
          get(ArtifactVersionStatus::deploying).isEqualTo(version6)
          get(ArtifactVersionStatus::previous).isEmpty()
        }

        expectThat(subject.isDeployingTo(manifest, stagingEnvironment.name)).isTrue()
      }

      test("promoting the same version again returns false") {
        expectCatching {
          clock.incrementBy(Duration.ofHours(1))
          subject.approveVersionFor(manifest, versionedSnapshotDebian, version1, testEnvironment.name)
        }
          .isSuccess()
          .isFalse()
      }

      test("promoting a new version returns true") {
        expectCatching {
          clock.incrementBy(Duration.ofHours(1))
          subject.approveVersionFor(manifest, versionedSnapshotDebian, version2, testEnvironment.name)
        }
          .isSuccess()
          .isTrue()
      }

      context("the version is marked as successfully deployed") {
        before {
          subject.markAsSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, version1, testEnvironment.name)
          subject.markAsSuccessfullyDeployedTo(manifest, versionedDockerArtifact, version6, stagingEnvironment.name)
        }

        test("the version is now considered successfully deployed") {
          expectThat(
            subject.wasSuccessfullyDeployedTo(
              manifest,
              versionedSnapshotDebian,
              version1,
              testEnvironment.name
            )
          )
            .isTrue()
          expectThat(
            subject.wasSuccessfullyDeployedTo(
              manifest,
              versionedDockerArtifact,
              version6,
              stagingEnvironment.name
            )
          )
            .isTrue()
        }

        test("the version is marked as currently deployed") {
          expectThat(subject.isCurrentlyDeployedTo(manifest, versionedSnapshotDebian, version1, testEnvironment.name))
            .isTrue()
          expectThat(
            subject.isCurrentlyDeployedTo(
              manifest,
              versionedDockerArtifact,
              version6,
              stagingEnvironment.name
            )
          )
            .isTrue()
        }

        test("the version is current in the environment") {
          expectThat(versionsIn(testEnvironment)) {
            get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version2, version3)
            get(ArtifactVersionStatus::current).isEqualTo(version1)
            get(ArtifactVersionStatus::deploying).isNull()
            get(ArtifactVersionStatus::previous).isEmpty()
          }

          expectThat(subject.isDeployingTo(manifest, testEnvironment.name)).isFalse()
        }

        test("the version is still current when it is re deployed (like if the base ami updates)") {
          subject.markAsDeployingTo(manifest, versionedSnapshotDebian, version1, testEnvironment.name)
          val versions = subject.getAllVersionsForEnvironment(versionedSnapshotDebian, manifest, testEnvironment.name)
          expect {
            that(versions.size).isEqualTo(3)
            that(versions.map { it.status }).containsExactlyInAnyOrder(listOf(DEPLOYING, PENDING, PENDING))
            that(versions.first { it.status == DEPLOYING }.publishedArtifact.version).isEqualTo(version1)
            that(versions.first { it.status == DEPLOYING }.isCurrent).isEqualTo(true)
          }
        }

        test("querying for current returns the full artifact") {
          val artifacts =
            subject.getArtifactVersionsByStatus(manifest, testEnvironment.name, listOf(CURRENT))
          expect {
            that(artifacts.size).isEqualTo(1)
            that(artifacts.first().version).isEqualTo(version1)
          }
          expectThat(
            subject.getCurrentlyDeployedArtifactVersion(
              manifest,
              versionedSnapshotDebian,
              testEnvironment.name
            )?.version
          ).isEqualTo(version1)
        }

        context("a new version is promoted to the same environment") {
          before {
            clock.incrementBy(Duration.ofHours(1))
            subject.approveVersionFor(manifest, versionedSnapshotDebian, version2, testEnvironment.name)
            subject.markAsDeployingTo(manifest, versionedSnapshotDebian, version2, testEnvironment.name)
          }

          test("the latest approved version changes") {
            expectThat(subject.latestVersionApprovedIn(manifest, versionedSnapshotDebian, testEnvironment.name))
              .isEqualTo(version2)
          }

          test("the version is not considered successfully deployed yet") {
            expectThat(
              subject.wasSuccessfullyDeployedTo(
                manifest,
                versionedSnapshotDebian,
                version2,
                testEnvironment.name
              )
            )
              .isFalse()
          }

          test("the new version is deploying in the environment") {
            expectThat(versionsIn(testEnvironment)) {
              get(ArtifactVersionStatus::pending).containsExactly(version3)
              get(ArtifactVersionStatus::current).isEqualTo(version1)
              get(ArtifactVersionStatus::deploying).isEqualTo(version2)
              get(ArtifactVersionStatus::previous).isEmpty()
            }

            expectThat(subject.isDeployingTo(manifest, testEnvironment.name)).isTrue()

          }

          context("the new version is marked as successfully deployed") {
            before {
              clock.incrementBy(Duration.ofMinutes(30))
              subject.markAsSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, version2, testEnvironment.name)
            }

            test("the old version is still considered successfully deployed") {
              expectThat(
                subject.wasSuccessfullyDeployedTo(
                  manifest,
                  versionedSnapshotDebian,
                  version1,
                  testEnvironment.name
                )
              )
                .isTrue()
            }

            test("the old version is not considered currently deployed") {
              expectThat(
                subject.isCurrentlyDeployedTo(
                  manifest,
                  versionedSnapshotDebian,
                  version1,
                  testEnvironment.name
                )
              )
                .isFalse()
            }

            test("the new version is also considered successfully deployed") {
              expectThat(
                subject.wasSuccessfullyDeployedTo(
                  manifest,
                  versionedSnapshotDebian,
                  version2,
                  testEnvironment.name
                )
              )
                .isTrue()
            }

            test("the new version is current in the environment") {
              expectThat(versionsIn(testEnvironment)) {
                get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version3)
                get(ArtifactVersionStatus::current).isEqualTo(version2)
                get(ArtifactVersionStatus::deploying).isNull()
                get(ArtifactVersionStatus::previous).containsExactly(version1)
              }

              expectThat(subject.isDeployingTo(manifest, testEnvironment.name)).isFalse()

              expectThat(
                subject.getCurrentlyDeployedArtifactVersion(
                  manifest,
                  versionedSnapshotDebian,
                  testEnvironment.name
                )?.version
              ).isEqualTo(version2)
            }
          }
        }

        context("there are two approved versions for the environment and the latter was deployed") {
          before {
            clock.incrementBy(Duration.ofHours(1))
            subject.approveVersionFor(manifest, versionedSnapshotDebian, version2, testEnvironment.name)
            subject.approveVersionFor(manifest, versionedSnapshotDebian, version3, testEnvironment.name)
            subject.markAsSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, version3, testEnvironment.name)
          }

          test("the lower version was marked as skipped") {
            val result = versionsIn(testEnvironment)
            expectThat(result) {
              get(ArtifactVersionStatus::pending).isEmpty()
              get(ArtifactVersionStatus::current).isEqualTo(version3)
              get(ArtifactVersionStatus::deploying).isNull()
              get(ArtifactVersionStatus::previous).containsExactly(version1)
              get(ArtifactVersionStatus::skipped).containsExactly(version2)
            }

            expectThat(subject.isDeployingTo(manifest, testEnvironment.name)).isFalse()
          }

          test("can get all information about the versions") {
            val versions = subject.getAllVersionsForEnvironment(versionedSnapshotDebian, manifest, testEnvironment.name)
            expect {
              that(versions.size).isEqualTo(3)
              that(versions.map { it.status }).containsExactlyInAnyOrder(listOf(CURRENT, PREVIOUS, SKIPPED))
              that(versions.first { it.status == CURRENT }.publishedArtifact.version).isEqualTo(version3)
              that(versions.first { it.status == PREVIOUS }.publishedArtifact.version).isEqualTo(version1)
              that(versions.first { it.status == SKIPPED }.publishedArtifact.version).isEqualTo(version2)
            }
          }
        }
      }

      context("a version of a different artifact is promoted to the environment") {
        before {
          clock.incrementBy(Duration.ofHours(1))
          subject.approveVersionFor(manifest, versionedReleaseDebian, version3, testEnvironment.name)
        }

        test("the approved version of the original artifact remains the same") {
          expectThat(subject.latestVersionApprovedIn(manifest, versionedSnapshotDebian, testEnvironment.name))
            .isEqualTo(version1)
        }

        test("the approved version of the new artifact matches") {
          expectThat(subject.latestVersionApprovedIn(manifest, versionedReleaseDebian, testEnvironment.name))
            .isEqualTo(version3)
        }
      }

      context("a different version of the same artifact is promoted to another environment") {
        before {
          clock.incrementBy(Duration.ofHours(1))
          subject.approveVersionFor(manifest, versionedSnapshotDebian, version2, stagingEnvironment.name)
        }

        test("the approved version in the original environment is unaffected") {
          expectThat(subject.latestVersionApprovedIn(manifest, versionedSnapshotDebian, testEnvironment.name))
            .isEqualTo(version1)
        }

        test("the approved version in the new environment matches") {
          expectThat(subject.latestVersionApprovedIn(manifest, versionedSnapshotDebian, stagingEnvironment.name))
            .isEqualTo(version2)
        }
      }
    }

    context("a version has been pinned to an environment") {
      before {
        clock.incrementBy(Duration.ofHours(1))
        subject.approveVersionFor(manifest, versionedReleaseDebian, version4, stagingEnvironment.name)
        subject.markAsSuccessfullyDeployedTo(manifest, versionedReleaseDebian, version4, stagingEnvironment.name)
        subject.approveVersionFor(manifest, versionedReleaseDebian, version5, stagingEnvironment.name)
        subject.markAsSuccessfullyDeployedTo(manifest, versionedReleaseDebian, version5, stagingEnvironment.name)
      }

      test("without a pin, latestVersionApprovedIn returns the latest approved version") {
        expectThat(subject.latestVersionApprovedIn(manifest, versionedReleaseDebian, stagingEnvironment.name))
          .isEqualTo(version5)
          .isNotEqualTo(pin1.version)
      }

      test("get env artifact version shows that artifact is not pinned") {
        val envArtifactSummary = subject.getArtifactSummaryInEnvironment(
          deliveryConfig = manifest,
          environmentName = pin1.targetEnvironment,
          artifactReference = versionedReleaseDebian.reference,
          version = version4
        )
        expectThat(envArtifactSummary)
          .isNotNull()
          .get { pinned }
          .isNull()
      }

      context("once pinned") {
        before {
          subject.pinEnvironment(manifest, pin1)
        }

        test("latestVersionApprovedIn prefers a pinned version over the latest approved version") {
          expectThat(subject.latestVersionApprovedIn(manifest, versionedReleaseDebian, stagingEnvironment.name))
            .isEqualTo(version4)
            .isEqualTo(pin1.version)
        }

        test("pinned version cannot be vetoed") {
          expectThat(
            subject.markAsVetoedIn(
              manifest,
              EnvironmentArtifactVeto(
                pin1.targetEnvironment,
                versionedReleaseDebian.reference,
                pin1.version,
                "sheepy",
                "this pin is baaaaaad"
              )
            )
          )
            .isFalse()
        }

        test("getting pinned environments shows the pin") {
          val pins = subject.getPinnedEnvironments(manifest)
          expectThat(pins)
            .hasSize(1)
            .isEqualTo(
              listOf(
                PinnedEnvironment(
                  deliveryConfigName = manifest.name,
                  targetEnvironment = pin1.targetEnvironment,
                  artifact = versionedReleaseDebian,
                  version = version4,
                  pinnedBy = pin1.pinnedBy,
                  pinnedAt = clock.instant(),
                  comment = pin1.comment
                )
              )
            )
        }

        test("get env artifact version shows that artifact is pinned") {
          val envArtifactSummary = subject.getArtifactSummaryInEnvironment(
            deliveryConfig = manifest,
            environmentName = pin1.targetEnvironment,
            artifactReference = versionedReleaseDebian.reference,
            version = version4
          )
          expect {
            that(envArtifactSummary).isNotNull()
            that(envArtifactSummary?.pinned).isEqualTo(
              ActionMetadata(
                by = pin1.pinnedBy,
                at = clock.instant(),
                comment = pin1.comment
              )
            )
          }
        }
      }
    }
  }
}
