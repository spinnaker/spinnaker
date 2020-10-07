package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilterSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.api.artifacts.BranchFilterSpec
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DEFAULT_MAX_ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.Job
import com.netflix.spinnaker.keel.api.artifacts.PullRequest
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.ActionMetadata
import com.netflix.spinnaker.keel.core.api.ArtifactVersionStatus
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
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
import java.time.Instant

abstract class ArtifactRepositoryTests<T : ArtifactRepository> : JUnit5Minutests {
  abstract fun factory(clock: Clock): T

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

    val debianFilteredByBranch =  DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "feature-branch",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilterSpec(
        branch = BranchFilterSpec(
          name = "my-feature-branch"
        )
      )
    )

    val debianFilteredByBranchStartingWith = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "feature-branch",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilterSpec(
        branch = BranchFilterSpec(
          startsWith = "feature-"
        )
      )
    )

    val debianFilteredByBranchPattern = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "feature-branch",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilterSpec(
        branch = BranchFilterSpec(
          regex = ".*feature.*"
        )
      )
    )

    val debianFilteredByPullRequest = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "pr",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilterSpec(
        pullRequestOnly = true
      )
    )

    val debianFilteredByPullRequestAndBranch = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "pr",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilterSpec(
        branch = BranchFilterSpec(
          name = "my-feature-branch"
        ),
        pullRequestOnly = true
      )
    )

    val featureBranchEnvironment = Environment("feature-branch")
    val testEnvironment = Environment("test")
    val stagingEnvironment = Environment("staging")
    val manifest = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(versionedSnapshotDebian, versionedReleaseDebian, versionedDockerArtifact, debianFilteredByBranch),
      environments = setOf(featureBranchEnvironment, testEnvironment, stagingEnvironment)
    )
    val version1 = "keeldemo-0.0.1~dev.8-h8.41595c4" // snapshot
    val version2 = "keeldemo-0.0.1~dev.9-h9.3d2c8ff" // snapshot
    val version3 = "keeldemo-0.0.1~dev.10-h10.1d2d542" // snapshot
    val version4 = "keeldemo-1.0.0-h11.518aea2" // release
    val version5 = "keeldemo-1.0.0-h12.4ea8a9d" // release
    val version6 = "master-h12.4ea8a9d"
    val versionBad = "latest"
    val versionOnly = "0.0.1~dev.8-h8.41595c4"

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
        startedAt = "yesterday",
        completedAt = "today",
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
  }

  open fun Fixture<T>.persist() {
    with(subject) {
      register(versionedSnapshotDebian)
      setOf(version1, version2, version3).forEach {
        storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(it, SNAPSHOT))
      }
      setOf(version4, version5).forEach {
        storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(it, RELEASE))
      }
      register(versionedReleaseDebian)
      setOf(version1, version2, version3).forEach {
        storeArtifactInstance(versionedReleaseDebian.toArtifactInstance(it, SNAPSHOT))
      }
      setOf(version4, version5).forEach {
        storeArtifactInstance(versionedReleaseDebian.toArtifactInstance(it, RELEASE))
      }
      register(versionedDockerArtifact)
      setOf(version6, versionBad).forEach {
        storeArtifactInstance(versionedDockerArtifact.toArtifactInstance(it))
      }
      register(debianFilteredByBranch)
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

  fun tests() = rootContext<Fixture<T>> {
    fixture { Fixture(factory(clock)) }

    after {
      subject.flush()
    }

    context("the artifact is unknown") {
      test("the artifact is not registered") {
        expectThat(subject.isRegistered(versionedSnapshotDebian.name, versionedSnapshotDebian.type)).isFalse()
      }

      test("storing a new version throws an exception") {
        expectThrows<NoSuchArtifactException> {
          subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(version1, SNAPSHOT))
        }
      }

      test("trying to get versions throws an exception") {
        expectThrows<NoSuchArtifactException> {
          subject.versions(versionedSnapshotDebian)
        }
      }
    }

    context("the artifact is known") {
      before {
        subject.register(versionedSnapshotDebian)
      }

      test("VM options are persisted and read correctly") {
        expectThat(subject.get(versionedSnapshotDebian.name, versionedSnapshotDebian.type, versionedSnapshotDebian.deliveryConfigName!!))
          .hasSize(1)
          .first()
          .isA<DebianArtifact>()
          .get { vmOptions }
          .isEqualTo(versionedSnapshotDebian.vmOptions)
      }

      test("re-registering the same artifact does not raise an exception") {
        subject.register(versionedSnapshotDebian)

        expectThat(subject.isRegistered(versionedSnapshotDebian.name, versionedSnapshotDebian.type)).isTrue()
      }

      context("no versions exist") {
        test("listing versions returns an empty list") {
          expectThat(subject.versions(versionedSnapshotDebian)).isEmpty()
        }
      }

      context("an artifact version already exists") {
        before {
          subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(version1, SNAPSHOT))
        }

        test("release status for the version is returned correctly") {
          expectThat(subject.getReleaseStatus(versionedSnapshotDebian, version1)).isEqualTo(SNAPSHOT)
        }

        test("registering the same version is a no-op") {
          val result = subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(version1, SNAPSHOT))
          expectThat(result).isFalse()
          expectThat(subject.versions(versionedSnapshotDebian)).hasSize(1)
        }

        test("adding a new version adds it to the list") {
          val result = subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(version2, SNAPSHOT))

          expectThat(result).isTrue()
          expectThat(subject.versions(versionedSnapshotDebian)).containsExactly(version2, version1)
        }

        test("querying for the list of versions returns both versions") {
          // status is stored on the artifact
          subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(version2, SNAPSHOT))
          expectThat(subject.versions(versionedSnapshotDebian)).containsExactly(version2, version1)
        }
      }

      context("sorting is consistent") {
        before {
          listOf(version1, version2, version3, version4, version5)
            .shuffled()
            .forEach {
              if (it == version4 || it == version5) {
                subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(it, RELEASE))
              } else {
                subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(it, SNAPSHOT))
              }
            }
        }

        test("versions are returned newest first and status is respected") {
          expect {
            that(subject.versions(versionedSnapshotDebian)).isEqualTo(listOf(version3, version2, version1))
            that(subject.versions(versionedReleaseDebian)).isEqualTo(listOf(version5, version4))
          }
        }
      }

      context("filtering based on status works") {
        before {
          persist()
        }

        context("debian") {
          test("querying for all returns all") {
            val artifactWithAll = versionedSnapshotDebian.copy(statuses = emptySet())
            expectThat(subject.versions(artifactWithAll)).containsExactly(version5, version4, version3, version2, version1)
          }

          test("querying with only release returns correct versions") {
            expectThat(subject.versions(versionedReleaseDebian)).containsExactly(version5, version4)
          }

          test("querying for limit returns limit") {
            val artifactWithAll = versionedSnapshotDebian.copy(statuses = emptySet())
            expectThat(subject.versions(artifactWithAll, 2)).containsExactly(version5, version4)
          }
        }

        context("docker") {
          test("querying for all returns all") {
            expectThat(subject.versions(versionedDockerArtifact.name, versionedDockerArtifact.type)).containsExactlyInAnyOrder(version6, versionBad)
          }

          test("querying the artifact filters out the bad tag") {
            expectThat(subject.versions(versionedDockerArtifact)).containsExactly(version6)
          }

          test("querying with a wrong strategy filters out everything") {
            val incorrectArtifact = DockerArtifact(
              name = "docker",
              deliveryConfigName = "my-manifest",
              reference = "docker-artifact",
              tagVersionStrategy = SEMVER_JOB_COMMIT_BY_JOB
            )
            expectThat(subject.versions(incorrectArtifact)).isEmpty()
          }
        }
      }

      context("limiting versions works") {
        before {
          (1..100).map { "1.0.$it"}.forEach {
            subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(it, SNAPSHOT))
          }
        }

        test("default cap applies with no limit specified") {
          expectThat(subject.versions(versionedSnapshotDebian)).hasSize(DEFAULT_MAX_ARTIFACT_VERSIONS)
        }

        test("limit parameter takes effect when specified") {
          expectThat(subject.versions(versionedSnapshotDebian, 20)).hasSize(20)
          expectThat(subject.versions(versionedSnapshotDebian, 100)).hasSize(100)
        }
      }
    }

    context("artifact promotion") {
      before {
        persist()
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
            subject.vetoedEnvironmentVersions(manifest)
          )
            .isEqualTo(
              listOf(
                EnvironmentArtifactVetoes(
                  deliveryConfigName = manifest.name,
                  targetEnvironment = testEnvironment.name,
                  artifact = versionedSnapshotDebian,
                  versions = mutableSetOf(version1)
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
          val v1summary = subject.getArtifactSummaryInEnvironment(manifest, testEnvironment.name, versionedSnapshotDebian.reference, version1)
          expectThat(v1summary)
            .isNotNull()
            .get { state }
            .isEqualTo("skipped")
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
          expectThat(subject.wasSuccessfullyDeployedTo(manifest, versionedDockerArtifact, version6, stagingEnvironment.name))
            .isFalse()
        }

        test("the version is deploying in the environment") {
          expectThat(versionsIn(testEnvironment)) {
            get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version2, version3)
            get(ArtifactVersionStatus::current).isNull()
            get(ArtifactVersionStatus::deploying).isEqualTo(version1)
            get(ArtifactVersionStatus::previous).isEmpty()
          }

          expectThat(versionsIn(stagingEnvironment, versionedDockerArtifact)) {
            get(ArtifactVersionStatus::pending).isEmpty()
            get(ArtifactVersionStatus::current).isNull()
            get(ArtifactVersionStatus::deploying).isEqualTo(version6)
            get(ArtifactVersionStatus::previous).isEmpty()
          }
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
            expectThat(subject.wasSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, version1, testEnvironment.name))
              .isTrue()
            expectThat(subject.wasSuccessfullyDeployedTo(manifest, versionedDockerArtifact, version6, stagingEnvironment.name))
              .isTrue()
          }

          test("the version is marked as currently deployed") {
            expectThat(subject.isCurrentlyDeployedTo(manifest, versionedSnapshotDebian, version1, testEnvironment.name))
              .isTrue()
            expectThat(subject.isCurrentlyDeployedTo(manifest, versionedDockerArtifact, version6, stagingEnvironment.name))
              .isTrue()
          }

          test("the version is current in the environment") {
            expectThat(versionsIn(testEnvironment)) {
              get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version2, version3)
              get(ArtifactVersionStatus::current).isEqualTo(version1)
              get(ArtifactVersionStatus::deploying).isNull()
              get(ArtifactVersionStatus::previous).isEmpty()
            }
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
              expectThat(subject.wasSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, version2, testEnvironment.name))
                .isFalse()
            }

            test("the new version is deploying in the environment") {
              expectThat(versionsIn(testEnvironment)) {
                get(ArtifactVersionStatus::pending).containsExactly(version3)
                get(ArtifactVersionStatus::current).isEqualTo(version1)
                get(ArtifactVersionStatus::deploying).isEqualTo(version2)
                get(ArtifactVersionStatus::previous).isEmpty()
              }
            }

            context("the new version is marked as successfully deployed") {
              before {
                subject.markAsSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, version2, testEnvironment.name)
              }

              test("the old version is still considered successfully deployed") {
                expectThat(subject.wasSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, version1, testEnvironment.name))
                  .isTrue()
              }

              test("the old version is not considered currently deployed") {
                expectThat(subject.isCurrentlyDeployedTo(manifest, versionedSnapshotDebian, version1, testEnvironment.name))
                  .isFalse()
              }

              test("the new version is also considered successfully deployed") {
                expectThat(subject.wasSuccessfullyDeployedTo(manifest, versionedSnapshotDebian, version2, testEnvironment.name))
                  .isTrue()
              }

              test("the new version is current in the environment") {
                expectThat(versionsIn(testEnvironment)) {
                  get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version3)
                  get(ArtifactVersionStatus::current).isEqualTo(version2)
                  get(ArtifactVersionStatus::deploying).isNull()
                  get(ArtifactVersionStatus::previous).containsExactly(version1)
                }
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
            expectThat(subject.markAsVetoedIn(manifest, EnvironmentArtifactVeto(pin1.targetEnvironment, versionedReleaseDebian.reference, pin1.version, "sheepy", "this pin is baaaaaad")))
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
              that(envArtifactSummary?.pinned).isEqualTo(ActionMetadata(by = pin1.pinnedBy, at = clock.instant(), comment = pin1.comment))
            }
          }
        }
      }
    }

    context("artifact approval querying") {
      before {
        persist()
        subject.approveVersionFor(manifest, versionedReleaseDebian, version1, testEnvironment.name)
        subject.approveVersionFor(manifest, versionedReleaseDebian, version2, testEnvironment.name)
        subject.approveVersionFor(manifest, versionedReleaseDebian, version3, testEnvironment.name)
      }

      test("we can query for all the versions and know they're approved") {
        expect {
          that(subject.isApprovedFor(manifest, versionedReleaseDebian, version1, testEnvironment.name)).isTrue()
          that(subject.isApprovedFor(manifest, versionedReleaseDebian, version2, testEnvironment.name)).isTrue()
          that(subject.isApprovedFor(manifest, versionedReleaseDebian, version3, testEnvironment.name)).isTrue()
        }
      }
    }

    context("getting all filters by type") {
      before {
        persist()
        subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(version4, FINAL))
        subject.storeArtifactInstance(versionedDockerArtifact.toArtifactInstance(version6, FINAL))
      }

      test("querying works") {
        expect {
          that(subject.getAll().size).isEqualTo(4)
          that(subject.getAll(DOCKER).size).isEqualTo(1)
          that(subject.getAll(DEBIAN).size).isEqualTo(3)
        }
      }
    }

    context("the latest version is vetoed") {
      before {
        subject.flush()
        persist()
        subject.approveVersionFor(manifest, versionedReleaseDebian, version4, stagingEnvironment.name)
        subject.approveVersionFor(manifest, versionedReleaseDebian, version5, stagingEnvironment.name)
        subject.markAsVetoedIn(manifest, EnvironmentArtifactVeto(stagingEnvironment.name, versionedReleaseDebian.reference, version5, "tester", "you bad"))
      }

      test("latestVersionApprovedIn reflects the veto") {
        expectThat(subject.latestVersionApprovedIn(manifest, versionedReleaseDebian, stagingEnvironment.name))
          .isEqualTo(version4)
      }

      test("vetoedEnvironmentVersions reflects the veto") {
        expectThat(subject.vetoedEnvironmentVersions(manifest))
          .isEqualTo(
            listOf(
              EnvironmentArtifactVetoes(
                deliveryConfigName = manifest.name,
                targetEnvironment = stagingEnvironment.name,
                artifact = versionedReleaseDebian,
                versions = mutableSetOf(version5)
              )
            )
          )
      }

      test("version status reflects the veto") {
        expectThat(versionsIn(stagingEnvironment, versionedReleaseDebian)) {
          get(ArtifactVersionStatus::vetoed).containsExactly(version5)
          get(ArtifactVersionStatus::approved).containsExactly(version4)
        }
      }

      test("get env artifact version shows that artifact is vetoed") {
        val envArtifactSummary = subject.getArtifactSummaryInEnvironment(
          deliveryConfig = manifest,
          environmentName = stagingEnvironment.name,
          artifactReference = versionedReleaseDebian.reference,
          version = version5
        )
        expect {
          that(envArtifactSummary).isNotNull()
          that(envArtifactSummary?.vetoed).isEqualTo(ActionMetadata(by = "tester", at = clock.instant(), comment = "you bad"))
        }
      }

      test("unveto the vetoed version") {
        subject.deleteVeto(manifest, versionedReleaseDebian, version5, stagingEnvironment.name)

        val envArtifactSummary = subject.getArtifactSummaryInEnvironment(
          deliveryConfig = manifest,
          environmentName = stagingEnvironment.name,
          artifactReference = versionedReleaseDebian.reference,
          version = version5
        )

        expectThat(subject.latestVersionApprovedIn(manifest, versionedReleaseDebian, stagingEnvironment.name))
          .isEqualTo(version5)
        expectThat(versionsIn(stagingEnvironment, versionedReleaseDebian)) {
          get(ArtifactVersionStatus::vetoed).isEmpty()
          get(ArtifactVersionStatus::approved).containsExactlyInAnyOrder(version4, version5)
        }
        expect {
          that(envArtifactSummary).isNotNull()
          that(envArtifactSummary?.vetoed).isNull()
        }
      }
    }

    context("artifact metadata exists") {
      before {
        subject.register(versionedSnapshotDebian)
        subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(version1, SNAPSHOT).copy(
          gitMetadata = artifactMetadata.gitMetadata,
          buildMetadata = artifactMetadata.buildMetadata
        ))
      }

      test("retrieves successfully") {
        val artifactVersion = subject.getArtifactInstance(versionedSnapshotDebian.name, versionedSnapshotDebian.type, version1, SNAPSHOT)!!

        expectThat(artifactVersion.buildMetadata)
          .isEqualTo(artifactMetadata.buildMetadata)

        expectThat(artifactVersion.gitMetadata)
          .isEqualTo(artifactMetadata.gitMetadata)
      }

      test("update with non-prefixed version works") {
        subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(versionOnly, SNAPSHOT).copy(
          gitMetadata = artifactMetadata.gitMetadata,
          buildMetadata = artifactMetadata.buildMetadata
        ))

        val artifactVersion = subject.getArtifactInstance(versionedSnapshotDebian.name, versionedSnapshotDebian.type, version1, SNAPSHOT)!!

        expectThat(artifactVersion.buildMetadata)
          .isEqualTo(artifactMetadata.buildMetadata)

        expectThat(artifactVersion.gitMetadata)
          .isEqualTo(artifactMetadata.gitMetadata)
      }
    }

    context("artifact creation timestamp exists") {
      val createdAt = Instant.now()

      before {
        subject.register(versionedSnapshotDebian)
        subject.storeArtifactInstance(versionedSnapshotDebian.toArtifactInstance(version1, SNAPSHOT, createdAt = createdAt))
      }

      test("retrieves timestamp successfully") {
        val artifactVersion = subject.getArtifactInstance(versionedSnapshotDebian.name, versionedSnapshotDebian.type, version1, SNAPSHOT)!!
        expectThat(artifactVersion.createdAt).isEqualTo(createdAt)
      }
    }

    context("artifact filtered by branch") {
      context("with branch name specified in the artifact spec") {
        // registers versions backwards to check that sorting by timestamp takes precedence
        val allVersions = (20 downTo 1).map { "keeldemo-not-a-version-$it" }

        before {
          subject.register(debianFilteredByBranch)
          allVersions.forEachIndexed { index, version ->
            subject.storeArtifactInstance(
              debianFilteredByBranch.toArtifactInstance(
                version = version,
                // half of the versions doesn't have a timestamp
                createdAt = if (index < 10) null else clock.tickMinutes(10)
              ).copy(
                gitMetadata = artifactMetadata.gitMetadata?.copy(
                  branch = debianFilteredByBranch.from!!.branch!!.name
                ),
                buildMetadata = artifactMetadata.buildMetadata
              )
            )
          }
        }

        test("returns \"versions\" with matching branch sorted by timestamp") {
          val versions = subject.versions(debianFilteredByBranch, 5)
          expectThat(versions).containsExactly(allVersions.reversed().subList(0, 5))
        }

        test("skips artifacts without a timestamp") {
          val versions = subject.versions(debianFilteredByBranch, 20)
          expectThat(versions).containsExactly(allVersions.reversed().subList(0, 10))
        }
      }

      context("with branch prefix specified in the artifact spec") {
        // registers versions backwards to check that sorting by timestamp takes precedence
        val allVersions = (20 downTo 1).map { "keeldemo-not-a-version-$it" }

        before {
          val prefix = debianFilteredByBranchStartingWith.from!!.branch!!.startsWith!!
          subject.register(debianFilteredByBranchStartingWith)
          allVersions.forEachIndexed { index, version ->
            subject.storeArtifactInstance(
              debianFilteredByBranchStartingWith.toArtifactInstance(
                version = version,
                createdAt = clock.tickMinutes(10)
              ).copy(
                gitMetadata = artifactMetadata.gitMetadata?.copy(
                  branch = if (index < 10) "not-a-matching-branch" else "$prefix-$index"
                ),
                buildMetadata = artifactMetadata.buildMetadata
              )
            )
          }
        }

        test("returns \"versions\" with matching branches sorted by timestamp") {
          val versions = subject.versions(debianFilteredByBranchStartingWith, 20)
          // only the first 10 versions have matching branches
          expectThat(versions).containsExactly(allVersions.reversed().subList(0, 10))
        }
      }

      context("with branch pattern specified in the artifact spec") {
        // registers versions backwards to check that sorting by timestamp takes precedence
        val allVersions = (20 downTo 1).map { "keeldemo-not-a-version-$it" }

        before {
          subject.register(debianFilteredByBranchPattern)
          allVersions.forEachIndexed { index, version ->
            subject.storeArtifactInstance(
              debianFilteredByBranchPattern.toArtifactInstance(
                version = version,
                createdAt = clock.tickMinutes(10)
              ).copy(
                gitMetadata = artifactMetadata.gitMetadata?.copy(
                  branch = when {
                    index < 5 -> "my-feature-x"
                    index < 10 -> "feature-branch-x"
                    index < 15 -> "myfeature"
                    else -> "a-non-matching-branch"
                  }
                ),
                buildMetadata = artifactMetadata.buildMetadata
              )
            )
          }
        }

        test("returns \"versions\" with matching branches sorted by timestamp") {
          val versions = subject.versions(debianFilteredByBranchPattern, 20)
          // first 5 have "a-non-matching-branch"
          expectThat(versions).containsExactly(allVersions.reversed().subList(5, 20))
        }
      }
    }

    context("artifact filtered by pull request") {
      // registers versions backwards to check that sorting by timestamp takes precedence
      val allVersions = (20 downTo 1).map { "keeldemo-not-a-version-$it" }

      before {
        subject.register(debianFilteredByPullRequest)
        allVersions.forEachIndexed { index, version ->
          subject.storeArtifactInstance(
            debianFilteredByPullRequest.toArtifactInstance(
              version = version,
              createdAt = clock.tickMinutes(10)
            ).copy(
              gitMetadata = artifactMetadata.gitMetadata!!.copy(
                // half the "versions" don't have pull request info
                pullRequest = if (index < 10) null else artifactMetadata.gitMetadata!!.pullRequest
              ),
              buildMetadata = artifactMetadata.buildMetadata
            )
          )
        }
      }

      test("returns \"versions\" with pull request sorted by timestamp") {
        val versions = subject.versions(debianFilteredByPullRequest, 20)
        // half the "versions" don't have pull request info
        expectThat(versions).containsExactly(allVersions.reversed().subList(0, 10))
      }
    }


    context("artifact filtered by pull request and branch") {
      // registers versions backwards to check that sorting by timestamp takes precedence
      val allVersions = (20 downTo 1).map { "keeldemo-not-a-version-$it" }

      before {
        subject.register(debianFilteredByPullRequestAndBranch)
        allVersions.forEachIndexed { index, version ->
          subject.storeArtifactInstance(
            debianFilteredByPullRequestAndBranch.toArtifactInstance(
              version = version,
              createdAt = clock.tickMinutes(10)
            ).copy(
              gitMetadata = artifactMetadata.gitMetadata!!.copy(
                // the last 5 versions don't have a matching branch
                branch = if (index in 0..4) null else debianFilteredByBranch.from!!.branch!!.name,
                // the 5 versions before that don't have pull request info
                pullRequest = if (index in 5..9) null else artifactMetadata.gitMetadata!!.pullRequest
              ),
              buildMetadata = artifactMetadata.buildMetadata
            )
          )
        }
      }

      test("returns \"versions\" with pull request and matching branch sorted by timestamp") {
        val versions = subject.versions(debianFilteredByPullRequestAndBranch, 20)
        // all versions should match
        expectThat(versions).containsExactly(allVersions.reversed().subList(0, 10))
      }
    }
  }
}
