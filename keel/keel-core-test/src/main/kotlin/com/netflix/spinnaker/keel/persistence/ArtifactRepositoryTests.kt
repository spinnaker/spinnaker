package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.api.artifacts.BranchFilter
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
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
import com.netflix.spinnaker.keel.core.api.ArtifactVersionVetoData
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.SKIPPED
import com.netflix.spinnaker.keel.core.api.PromotionStatus.VETOED
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expect
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
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

abstract class ArtifactRepositoryTests<T : ArtifactRepository> : JUnit5Minutests {
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
      reference = "feature-branch",
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
      reference = "feature-branch",
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
      reference = "feature-branch-pattern",
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
      reference = "feature-pr",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      from = ArtifactOriginFilter(
        pullRequestOnly = true
      )
    )

    val debianFilteredByPullRequestAndBranch = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "feature-pr-and-branch",
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
        debianFilteredByPullRequestAndBranch
      ),
      environments = setOf(testEnvironment, stagingEnvironment)
    )
    val version1 = "keeldemo-0.0.1~dev.8-h8.41595c4" // snapshot
    val version2 = "keeldemo-0.0.1~dev.9-h9.3d2c8ff" // snapshot
    val version3 = "keeldemo-0.0.1~dev.10-h10.1d2d542" // snapshot
    val version4 = "keeldemo-1.0.0-h11.518aea2" // release
    val version5 = "keeldemo-1.0.0-h12.4ea8a9d" // release
    val version6 = "master-h12.4ea8a9d"
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

    after {
      subject.flush()
    }

    context("the artifact is unknown") {
      test("the artifact is not registered") {
        expectThat(subject.isRegistered(versionedSnapshotDebian.name, versionedSnapshotDebian.type)).isFalse()
      }

      test("storing a new version throws an exception") {
        expectThrows<NoSuchArtifactException> {
          subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(version1, SNAPSHOT))
        }
      }

      test("trying to get versions throws an exception") {
        expectThrows<NoSuchArtifactException> {
          subject.versions(versionedSnapshotDebian, limit)
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

      test("changing an artifact name works") {
        subject.register(versionedSnapshotDebian.copy(name = "keeldemo-but-a-different-name"))
        val artifact = subject.get(versionedSnapshotDebian.deliveryConfigName!!, versionedSnapshotDebian.reference)
        expectThat(artifact.name).isEqualTo("keeldemo-but-a-different-name")
      }

      context("no versions exist") {
        test("listing versions returns an empty list") {
          expectThat(subject.versions(versionedSnapshotDebian, limit)).isEmpty()
        }

        test("no version is deploying") {
          expectThat(subject.isDeployingTo(manifest, testEnvironment.name)).isFalse()
        }
      }

      context("an artifact version already exists") {
        before {
          subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(version1, SNAPSHOT))
        }

        test("release status for the version is returned correctly") {
          expectThat(subject.getReleaseStatus(versionedSnapshotDebian, version1)).isEqualTo(SNAPSHOT)
        }

        test("registering the same version is a no-op") {
          val result = subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(version1, SNAPSHOT))
          expectThat(result).isFalse()
          expectThat(subject.versions(versionedSnapshotDebian, limit)).hasSize(1)
        }

        test("adding a new version adds it to the list") {
          val result = subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(version2, SNAPSHOT))

          expectThat(result).isTrue()
          expectThat(subject.versions(versionedSnapshotDebian, limit).map { it.version })
            .containsExactly(version2, version1)
        }

        test("querying for the list of versions returns both versions") {
          // status is stored on the artifact
          subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(version2, SNAPSHOT))
          expectThat(subject.versions(versionedSnapshotDebian, limit).map { it.version })
            .containsExactly(version2, version1)
        }
      }

      context("sorting is consistent") {
        before {
          listOf(version1, version2, version3, version4, version5)
            .shuffled()
            .forEach {
              if (it == version4 || it == version5) {
                subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(it, RELEASE))
              } else {
                subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(it, SNAPSHOT))
              }
            }
        }

        test("versions are returned newest first and status is respected") {
          expect {
            that(subject.versions(versionedSnapshotDebian, limit).map { it.version })
              .isEqualTo(listOf(version3, version2, version1))
            that(subject.versions(versionedReleaseDebian, limit).map { it.version })
              .isEqualTo(listOf(version5, version4))
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
            expectThat(subject.versions(artifactWithAll, limit).map { it.version })
              .containsExactly(version5, version4, version3, version2, version1)
          }

          test("querying with only release returns correct versions") {
            expectThat(subject.versions(versionedReleaseDebian, limit).map { it.version })
              .containsExactly(version5, version4)
          }

          test("querying for limit returns limit") {
            val artifactWithAll = versionedSnapshotDebian.copy(statuses = emptySet())
            expectThat(subject.versions(artifactWithAll, 2).map { it.version })
              .containsExactly(version5, version4)
          }
        }

        context("docker") {
          test("querying for all returns all") {
            expectThat(subject.versions(versionedDockerArtifact, limit).map { it.version })
              .containsExactlyInAnyOrder(version6)
          }

          test("querying the artifact filters out the bad tag") {
            expectThat(subject.versions(versionedDockerArtifact, limit).map { it.version })
              .containsExactly(version6)
          }

          test("querying with a wrong strategy filters out everything") {
            val incorrectArtifact = DockerArtifact(
              name = "docker",
              deliveryConfigName = "my-manifest",
              reference = "docker-artifact",
              tagVersionStrategy = SEMVER_JOB_COMMIT_BY_JOB
            )
            expectThat(subject.versions(incorrectArtifact, limit)).isEmpty()
          }
        }
      }

      context("limiting versions works") {
        before {
          (1..100).map { "1.0.$it" }.forEach {
            subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(it, SNAPSHOT))
          }
        }

        test("limit parameter takes effect when specified") {
          expectThat(subject.versions(versionedSnapshotDebian, 20)).hasSize(20)
          expectThat(subject.versions(versionedSnapshotDebian, 100)).hasSize(100)
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
        subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(version4, FINAL))
        subject.storeArtifactVersion(versionedDockerArtifact.toArtifactVersion(version6, FINAL))
      }

      test("querying works") {
        expect {
          that(subject.getAll().size).isEqualTo(7)
          that(subject.getAll(DOCKER).size).isEqualTo(1)
          that(subject.getAll(DEBIAN).size).isEqualTo(6)
        }
      }
    }

    context("the latest version is vetoed") {
      before {
        subject.flush()
        persist()
        subject.approveVersionFor(manifest, versionedReleaseDebian, version4, stagingEnvironment.name)
        subject.approveVersionFor(manifest, versionedReleaseDebian, version5, stagingEnvironment.name)
        subject.markAsSuccessfullyDeployedTo(manifest, versionedReleaseDebian, version5, stagingEnvironment.name)
        subject.markAsVetoedIn(manifest, EnvironmentArtifactVeto(stagingEnvironment.name, versionedReleaseDebian.reference, version5, "tester", "you bad"))
      }

      test("latestVersionApprovedIn reflects the veto") {
        expectThat(subject.latestVersionApprovedIn(manifest, versionedReleaseDebian, stagingEnvironment.name))
          .isEqualTo(version4)
      }

      test("vetoedEnvironmentVersions reflects the veto") {
        expectThat(subject.vetoedEnvironmentVersions(manifest).map {
          it.copy(versions = it.versions.map { v -> v.copy(vetoedAt = null) }.toMutableSet())
        })
          .isEqualTo(
            listOf(
              EnvironmentArtifactVetoes(
                deliveryConfigName = manifest.name,
                targetEnvironment = stagingEnvironment.name,
                artifact = versionedReleaseDebian,
                versions = mutableSetOf(ArtifactVersionVetoData(version = version5, vetoedBy = "tester", vetoedAt = null, comment = "you bad"))
              )
            )
          )
      }

      test("version status reflects the veto") {
        expectThat(versionsIn(stagingEnvironment, versionedReleaseDebian)) {
          get(ArtifactVersionStatus::vetoed).containsExactly(version5)
          get(ArtifactVersionStatus::current).isEqualTo(version5)
        }
      }

      test("current version is still the vetoed version") {
        expectThat(subject.getCurrentlyDeployedArtifactVersion(manifest, versionedReleaseDebian, stagingEnvironment.name)?.version).isEqualTo(version5)
      }

      test("can get all information about the versions") {
        val versions = subject.getAllVersionsForEnvironment(versionedReleaseDebian, manifest, stagingEnvironment.name)
        expectThat(versions.size).isEqualTo(2)
        expectThat(versions.map { it.status }).containsExactlyInAnyOrder(listOf(VETOED, SKIPPED))
        expectThat(versions.first { it.status == SKIPPED }.publishedArtifact.version).isEqualTo(version4)
        expectThat(versions.first { it.status == VETOED }.publishedArtifact.version).isEqualTo(version5)
        expectThat(versions.first { it.status == VETOED }.isCurrent).isEqualTo(true)
      }

      test("get env artifact version shows that artifact is vetoed") {
        val envArtifactSummaries = subject.getArtifactSummariesInEnvironment(
          deliveryConfig = manifest,
          environmentName = stagingEnvironment.name,
          artifactReference = versionedReleaseDebian.reference,
          versions = listOf(version5)
        )
        expect {
          that(envArtifactSummaries).isNotEmpty()
          that(envArtifactSummaries.firstOrNull()?.vetoed).isEqualTo(ActionMetadata(by = "tester", at = clock.instant(), comment = "you bad"))
        }
      }

      test("unveto the vetoed version") {
        subject.deleteVeto(manifest, versionedReleaseDebian, version5, stagingEnvironment.name)

        val envArtifactSummaries = subject.getArtifactSummariesInEnvironment(
          deliveryConfig = manifest,
          environmentName = stagingEnvironment.name,
          artifactReference = versionedReleaseDebian.reference,
          versions = listOf(version5)
        )

        expectThat(subject.latestVersionApprovedIn(manifest, versionedReleaseDebian, stagingEnvironment.name))
          .isEqualTo(version5)
        expectThat(versionsIn(stagingEnvironment, versionedReleaseDebian)) {
          get(ArtifactVersionStatus::vetoed).isEmpty()
          get(ArtifactVersionStatus::approved).isEmpty()
          get(ArtifactVersionStatus::current).isEqualTo(version5)
        }
        expect {
          that(envArtifactSummaries).isNotEmpty()
          that(envArtifactSummaries.firstOrNull()?.vetoed).isNull()
        }
      }
    }

    context("artifact metadata exists") {
      before {
        subject.register(versionedSnapshotDebian)
        subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(version1, SNAPSHOT).copy(
          gitMetadata = artifactMetadata.gitMetadata,
          buildMetadata = artifactMetadata.buildMetadata
        ))
      }

      test("retrieves successfully") {
        val artifactVersion = subject.getArtifactVersion(versionedSnapshotDebian, version1, SNAPSHOT)!!

        expectThat(artifactVersion.buildMetadata)
          .isEqualTo(artifactMetadata.buildMetadata)

        expectThat(artifactVersion.gitMetadata)
          .isEqualTo(artifactMetadata.gitMetadata)
      }

      test("update with non-prefixed version works") {
        subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(versionOnly, SNAPSHOT).copy(
          gitMetadata = artifactMetadata.gitMetadata,
          buildMetadata = artifactMetadata.buildMetadata
        ))

        val artifactVersion = subject.getArtifactVersion(versionedSnapshotDebian, version1, SNAPSHOT)!!

        expectThat(artifactVersion.buildMetadata)
          .isEqualTo(artifactMetadata.buildMetadata)

        expectThat(artifactVersion.gitMetadata)
          .isEqualTo(artifactMetadata.gitMetadata)
      }
    }

    context("artifact creation timestamp exists") {
      // We truncate this since we're using a serialization to java that reduces the level of precision
      // and later comparisons break otherwise.  This is needed to work with generated columns in
      // certain databases.  See the PrecisionSqlSerializer class for more info
      val createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS)

      before {
        subject.register(versionedSnapshotDebian)
        subject.storeArtifactVersion(versionedSnapshotDebian.toArtifactVersion(version1, SNAPSHOT, createdAt = createdAt))
      }

      test("retrieves timestamp successfully") {
        val artifactVersion = subject.getArtifactVersion(versionedSnapshotDebian, version1, SNAPSHOT)!!
        expectThat(artifactVersion.createdAt).isEqualTo(createdAt)
      }
    }

    context("artifact filtered by branch") {
      context("with branch name specified in the artifact spec") {
        // registers versions backwards to check that sorting by timestamp takes precedence
        val allVersions = (20 downTo 1).map { "keeldemo-any-string-$it" }

        before {
          subject.register(debianFilteredByBranch)
          allVersions.forEachIndexed { index, version ->
            subject.storeArtifactVersion(
              debianFilteredByBranch.toArtifactVersion(
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
          expectThat(versions.map { it.version })
            .containsExactly(allVersions.reversed().subList(0, 5))
        }

        test("skips artifacts without a timestamp") {
          val versions = subject.versions(debianFilteredByBranch, 20)
          expectThat(versions.map { it.version })
            .containsExactly(allVersions.reversed().subList(0, 10))
        }
      }

      context("with branch prefix specified in the artifact spec") {
        // registers versions backwards to check that sorting by timestamp takes precedence
        val allVersions = (20 downTo 1).map { "keeldemo-any-string-$it" }

        before {
          val prefix = debianFilteredByBranchPrefix.from!!.branch!!.startsWith!!
          subject.register(debianFilteredByBranchPrefix)
          allVersions.forEachIndexed { index, version ->
            storeArtifactVersionWithBranch(
              artifact = debianFilteredByBranchPrefix,
              version = version,
              branch = if (index < 10) "not-a-matching-branch" else "${prefix}my-feature-$index"
            )
          }
        }

        test("returns \"versions\" with matching branches sorted by timestamp") {
          val versions = subject.versions(debianFilteredByBranchPrefix, 20)
          // only the first 10 versions have matching branches
          expectThat(versions.map { it.version })
            .containsExactly(allVersions.reversed().subList(0, 10))
        }
      }

      context("with branch pattern specified in the artifact spec") {
        // registers versions backwards to check that sorting by timestamp takes precedence
        val allVersions = (20 downTo 1).map { "keeldemo-any-string-$it" }

        before {
          storeVersionsForDebianFilteredByBranchPattern(allVersions)
        }

        test("returns \"versions\" with matching branches sorted by timestamp") {
          val versions = subject.versions(debianFilteredByBranchPattern, 20)
          // first 5 have "a-non-matching-branch"
          expectThat(versions.map { it.version })
            .containsExactly(allVersions.reversed().subList(5, 20))
        }
      }
    }

    context("artifact filtered by pull request") {
      // registers versions backwards to check that sorting by timestamp takes precedence
      val allVersions = (20 downTo 1).map { "keeldemo-any-string-$it" }

      before {
        storeVersionsForDebianFilteredByPullRequest(allVersions)
      }

      test("returns \"versions\" with pull request sorted by timestamp") {
        val versions = subject.versions(debianFilteredByPullRequest, 20)
        // half the "versions" don't have pull request info
        expectThat(versions.map { it.version })
          .containsExactly(allVersions.reversed().subList(0, 10))
      }
    }

    context("artifact filtered by pull request and branch") {
      // registers versions backwards to check that sorting by timestamp takes precedence
      val allVersions = (20 downTo 1).map { "keeldemo-any-string-$it" }

      before {
        subject.register(debianFilteredByPullRequestAndBranch)
        allVersions.forEachIndexed { index, version ->
          subject.storeArtifactVersion(
            debianFilteredByPullRequestAndBranch.toArtifactVersion(
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
        expectThat(versions.map { it.version })
          .containsExactly(allVersions.reversed().subList(0, 10))
      }
    }

    context("artifact versions by promotion status") {
      before {
        persist(manifest)
        subject.register(versionedReleaseDebian)
        subject.storeArtifactVersion(versionedReleaseDebian.toArtifactVersion(version1, RELEASE).copy(
          gitMetadata = artifactMetadata.gitMetadata
        ))
        subject.storeArtifactVersion(versionedReleaseDebian.toArtifactVersion(version2, RELEASE).copy(
          gitMetadata = artifactMetadata.gitMetadata?.copy(
            commit = "12345"
          ),
        ))
        subject.markAsSuccessfullyDeployedTo(manifest, versionedReleaseDebian, version1, testEnvironment.name)
      }

      test("no versions exists if not persisted") {
        expectThat(subject.getArtifactVersionByPromotionStatus(manifest, testEnvironment.name, versionedReleaseDebian, PromotionStatus.PREVIOUS))
          .isNull()
      }

      test("get artifact versions for deploying status") {
        expectThat(subject.getArtifactVersionByPromotionStatus(manifest, testEnvironment.name, versionedReleaseDebian, CURRENT)?.gitMetadata)
          .isEqualTo(artifactMetadata.gitMetadata)
      }

      test("get a single results (and newest) data per status") {
        subject.markAsSuccessfullyDeployedTo(manifest, versionedReleaseDebian, version2, testEnvironment.name)
        expectThat(subject.getArtifactVersionByPromotionStatus(manifest, testEnvironment.name, versionedReleaseDebian, CURRENT)?.gitMetadata)
          .get { this?.commit }.isEqualTo("12345")
      }

      test("get artifact version by promotion status and the version it replaced") {
        subject.markAsSuccessfullyDeployedTo(manifest, versionedReleaseDebian, version2, testEnvironment.name)
        expectThat(subject.getArtifactVersionByPromotionStatus(manifest, testEnvironment.name, versionedReleaseDebian, PromotionStatus.PREVIOUS, version2))
          .get { this?.version }.isEqualTo("keeldemo-0.0.1~dev.8-h8.41595c4")
      }

      test("unsupported promotion status throws exception") {
        expectThrows<IllegalArgumentException> {
          subject.getArtifactVersionByPromotionStatus(manifest, testEnvironment.name, versionedReleaseDebian, PromotionStatus.DEPLOYING)
        }
      }
    }

    context("pinned version") {
      before {
        persist(manifest)
        subject.register(versionedReleaseDebian)
      }
      test("there isn't any pinned version in any environment") {
        expectThat(subject.getPinnedVersion(manifest, testEnvironment.name, versionedReleaseDebian.reference))
          .isNull()
        expectThat(subject.getPinnedVersion(manifest, stagingEnvironment.name, versionedReleaseDebian.reference))
          .isNull()
      }

      test("there is one pinned version in test, non in staging") {
        subject.pinEnvironment(manifest, EnvironmentArtifactPin(testEnvironment.name, versionedReleaseDebian.reference, version1, null, null))
        expectThat(subject.getPinnedVersion(manifest, testEnvironment.name, versionedReleaseDebian.reference))
          .isEqualTo(version1)
        expectThat(subject.getPinnedVersion(manifest, stagingEnvironment.name, versionedReleaseDebian.reference))
          .isNull()
      }

      test("pinned two versions, get only the latest pinned version") {
        subject.pinEnvironment(manifest, EnvironmentArtifactPin(testEnvironment.name, versionedReleaseDebian.reference, version1, null, null))
        subject.pinEnvironment(manifest, EnvironmentArtifactPin(testEnvironment.name, versionedReleaseDebian.reference, version2, null, null))
        expectThat(subject.getPinnedVersion(manifest, testEnvironment.name, versionedReleaseDebian.reference))
          .isEqualTo(version2)
      }
    }
  }
}
