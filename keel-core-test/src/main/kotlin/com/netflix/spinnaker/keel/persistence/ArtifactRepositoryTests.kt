package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.ArtifactStatus.RELEASE
import com.netflix.spinnaker.keel.api.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.api.ArtifactType.deb
import com.netflix.spinnaker.keel.api.ArtifactType.docker
import com.netflix.spinnaker.keel.api.ArtifactVersionStatus
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.EnvironmentArtifactPin
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Clock
import java.time.Duration
import strikt.api.expect
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNull
import strikt.assertions.isTrue
import strikt.assertions.succeeded

abstract class ArtifactRepositoryTests<T : ArtifactRepository> : JUnit5Minutests {
  abstract fun factory(clock: Clock): T

  val clock = MutableClock()

  open fun T.flush() {}

  data class Fixture<T : ArtifactRepository>(
    val subject: T
  ) {
    // the artifact built off a feature branch
    val artifact1 = DebianArtifact("keeldemo", deliveryConfigName = "my-manifest", reference = "candidate", statuses = listOf(SNAPSHOT))
    // the artifact built off of master
    val artifact2 = DebianArtifact("keeldemo", deliveryConfigName = "my-manifest", reference = "master", statuses = listOf(RELEASE))
    val artifact3 = DockerArtifact("docker", deliveryConfigName = "my-manifest", reference = "docker-artifact")
    val environment1 = Environment("test")
    val environment2 = Environment("staging")
    val manifest = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact1, artifact2, artifact3),
      environments = setOf(environment1, environment2)
    )
    val version1 = "keeldemo-0.0.1~dev.8-h8.41595c4" // snapshot
    val version2 = "keeldemo-0.0.1~dev.9-h9.3d2c8ff" // snapshot
    val version3 = "keeldemo-0.0.1~dev.10-h10.1d2d542" // snapshot
    val version4 = "keeldemo-1.0.0-h11.518aea2" // release
    val version5 = "keeldemo-1.0.0-h12.4ea8a9d" // release
    val version6 = "master-h12.4ea8a9d"

    val pin1 = EnvironmentArtifactPin(
      deliveryConfigName = manifest.name,
      targetEnvironment = environment2.name, // staging
      reference = artifact2.reference,
      type = artifact2.type.name,
      version = version4, // the older release build
      pinnedBy = "keel@spinnaker",
      comment = "fnord")
  }

  open fun Fixture<T>.persist() {
    with(subject) {
      register(artifact1)
      setOf(version1, version2, version3).forEach {
        store(artifact1, it, SNAPSHOT)
      }
      setOf(version4, version5).forEach {
        store(artifact1, it, RELEASE)
      }
      register(artifact2)
      setOf(version1, version2, version3).forEach {
        store(artifact2, it, SNAPSHOT)
      }
      setOf(version4, version5).forEach {
        store(artifact2, it, RELEASE)
      }
      register(artifact3)
      store(artifact3, version6, null)
    }
    persist(manifest)
  }

  abstract fun persist(manifest: DeliveryConfig)

  private fun Fixture<T>.versionsIn(
    environment: Environment,
    artifact: DeliveryArtifact = artifact1
  ): ArtifactVersionStatus {
    return subject
      .versionsByEnvironment(manifest)
      .first { it.name == environment.name }
      .artifacts
      .first { it.name == artifact.name && it.type == artifact.type }
      .versions
  }

  fun tests() = rootContext<Fixture<T>> {
    fixture { Fixture(factory(clock)) }

    after {
      subject.flush()
    }

    context("the artifact is unknown") {
      test("the artifact is not registered") {
        expectThat(subject.isRegistered(artifact1.name, artifact1.type)).isFalse()
      }

      test("storing a new version throws an exception") {
        expectThrows<NoSuchArtifactException> {
          subject.store(artifact1, version1, SNAPSHOT)
        }
      }

      test("trying to get versions throws an exception") {
        expectThrows<NoSuchArtifactException> {
          subject.versions(artifact1)
        }
      }
    }

    context("the artifact is known") {
      before {
        subject.register(artifact1)
      }

      test("re-registering the same artifact does not raise an exception") {
        subject.register(artifact1)

        expectThat(subject.isRegistered(artifact1.name, artifact1.type)).isTrue()
      }

      context("no versions exist") {
        test("listing versions returns an empty list") {
          expectThat(subject.versions(artifact1)).isEmpty()
        }
      }

      context("an artifact version already exists") {
        before {
          subject.store(artifact1, version1, SNAPSHOT)
        }

        test("registering the same version is a no-op") {
          val result = subject.store(artifact1, version1, SNAPSHOT)
          expectThat(result).isFalse()
          expectThat(subject.versions(artifact1)).hasSize(1)
        }

        test("adding a new version adds it to the list") {
          val result = subject.store(artifact1, version2, SNAPSHOT)

          expectThat(result).isTrue()
          expectThat(subject.versions(artifact1)).containsExactly(version2, version1)
        }

        test("querying the list for returns both artifacts") {
          // status is stored on the artifact
          subject.store(artifact1, version2, SNAPSHOT)
          expectThat(subject.versions(artifact1)).containsExactly(version2, version1)
        }
      }

      context("sorting is consistent") {
        before {
          listOf(version1, version2, version3, version4, version5)
            .shuffled()
            .forEach {
              if (it == version4 || it == version5) {
                subject.store(artifact1, it, RELEASE)
              } else {
                subject.store(artifact1, it, SNAPSHOT)
              }
            }
        }

        test("versions are returned newest first and status is respected") {
          expect {
            that(subject.versions(artifact1)).isEqualTo(listOf(version3, version2, version1))
            that(subject.versions(artifact2)).isEqualTo(listOf(version5, version4))
          }
        }
      }

      context("filtering based on status works") {
        before {
          persist()
        }

        test("querying for all returns all") {
          val artifactWithAll = artifact1.copy(statuses = emptyList())
          expectThat(subject.versions(artifactWithAll)).containsExactly(version5, version4, version3, version2, version1)
        }

        test("querying with only release returns correct versions") {
          expectThat(subject.versions(artifact2)).containsExactly(version5, version4)
        }
      }
    }

    context("artifact promotion") {
      before {
        persist()
      }

      context("no version has been promoted to an environment") {
        test("the approved version for that environment is null") {
          expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment1.name))
            .isNull()
        }

        test("versions are not considered successfully deployed") {
          setOf(version1, version2, version3).forEach {
            expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, it, environment1.name))
              .isFalse()
          }
        }

        test("the artifact version is pending in the environment") {
          expectThat(versionsIn(environment1)) {
            get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version1, version2, version3)
            get(ArtifactVersionStatus::current).isNull()
            get(ArtifactVersionStatus::deploying).isNull()
            get(ArtifactVersionStatus::previous).isEmpty()
          }
        }
      }

      context("a version has been promoted to an environment") {
        before {
          clock.incrementBy(Duration.ofHours(1))
          subject.approveVersionFor(manifest, artifact1, version1, environment1.name)
          subject.markAsDeployingTo(manifest, artifact1, version1, environment1.name)
          subject.approveVersionFor(manifest, artifact3, version6, environment2.name)
          subject.markAsDeployingTo(manifest, artifact3, version6, environment2.name)
        }

        test("the approved version for that environment matches") {
          // debian
          expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment1.name))
            .isEqualTo(version1)
          // docker
          expectThat(subject.latestVersionApprovedIn(manifest, artifact3, environment2.name))
            .isEqualTo(version6)
        }

        test("the version is not considered successfully deployed yet") {
          expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, version1, environment1.name))
            .isFalse()
          expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact3, version6, environment2.name))
            .isFalse()
        }

        test("the version is deploying in the environment") {
          expectThat(versionsIn(environment1)) {
            get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version2, version3)
            get(ArtifactVersionStatus::current).isNull()
            get(ArtifactVersionStatus::deploying).isEqualTo(version1)
            get(ArtifactVersionStatus::previous).isEmpty()
          }

          expectThat(versionsIn(environment2, artifact3)) {
            get(ArtifactVersionStatus::pending).isEmpty()
            get(ArtifactVersionStatus::current).isNull()
            get(ArtifactVersionStatus::deploying).isEqualTo(version6)
            get(ArtifactVersionStatus::previous).isEmpty()
          }
        }

        test("promoting the same version again returns false") {
          expectCatching {
            clock.incrementBy(Duration.ofHours(1))
            subject.approveVersionFor(manifest, artifact1, version1, environment1.name)
          }
            .succeeded()
            .isFalse()
        }

        test("promoting a new version returns true") {
          expectCatching {
            clock.incrementBy(Duration.ofHours(1))
            subject.approveVersionFor(manifest, artifact1, version2, environment1.name)
          }
            .succeeded()
            .isTrue()
        }

        context("the version is marked as successfully deployed") {
          before {
            subject.markAsSuccessfullyDeployedTo(manifest, artifact1, version1, environment1.name)
            subject.markAsSuccessfullyDeployedTo(manifest, artifact3, version6, environment2.name)
          }

          test("the version is now considered successfully deployed") {
            expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, version1, environment1.name))
              .isTrue()
            expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact3, version6, environment2.name))
              .isTrue()
          }

          test("the version is current in the environment") {
            expectThat(versionsIn(environment1)) {
              get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version2, version3)
              get(ArtifactVersionStatus::current).isEqualTo(version1)
              get(ArtifactVersionStatus::deploying).isNull()
              get(ArtifactVersionStatus::previous).isEmpty()
            }
          }

          context("a new version is promoted to the same environment") {
            before {
              clock.incrementBy(Duration.ofHours(1))
              subject.approveVersionFor(manifest, artifact1, version2, environment1.name)
              subject.markAsDeployingTo(manifest, artifact1, version2, environment1.name)
            }

            test("the latest approved version changes") {
              expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment1.name))
                .isEqualTo(version2)
            }

            test("the version is not considered successfully deployed yet") {
              expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, version2, environment1.name))
                .isFalse()
            }

            test("the new version is deploying in the environment") {
              expectThat(versionsIn(environment1)) {
                get(ArtifactVersionStatus::pending).containsExactly(version3)
                get(ArtifactVersionStatus::current).isEqualTo(version1)
                get(ArtifactVersionStatus::deploying).isEqualTo(version2)
                get(ArtifactVersionStatus::previous).isEmpty()
              }
            }

            context("the new version is marked as successfully deployed") {
              before {
                subject.markAsSuccessfullyDeployedTo(manifest, artifact1, version2, environment1.name)
              }

              test("the old version is still considered successfully deployed") {
                expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, version1, environment1.name))
                  .isTrue()
              }

              test("the new version is also considered successfully deployed") {
                expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, version2, environment1.name))
                  .isTrue()
              }

              test("the new version is current in the environment") {
                expectThat(versionsIn(environment1)) {
                  get(ArtifactVersionStatus::pending).containsExactlyInAnyOrder(version3)
                  get(ArtifactVersionStatus::current).isEqualTo(version2)
                  get(ArtifactVersionStatus::deploying).isNull()
                  get(ArtifactVersionStatus::previous).containsExactly(version1)
                }
              }
            }
          }
        }

        context("a version of a different artifact is promoted to the environment") {
          before {
            clock.incrementBy(Duration.ofHours(1))
            subject.approveVersionFor(manifest, artifact2, version3, environment1.name)
          }

          test("the approved version of the original artifact remains the same") {
            expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment1.name))
              .isEqualTo(version1)
          }

          test("the approved version of the new artifact matches") {
            expectThat(subject.latestVersionApprovedIn(manifest, artifact2, environment1.name))
              .isEqualTo(version3)
          }
        }

        context("a different version of the same artifact is promoted to another environment") {
          before {
            clock.incrementBy(Duration.ofHours(1))
            subject.approveVersionFor(manifest, artifact1, version2, environment2.name)
          }

          test("the approved version in the original environment is unaffected") {
            expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment1.name))
              .isEqualTo(version1)
          }

          test("the approved version in the new environment matches") {
            expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment2.name))
              .isEqualTo(version2)
          }
        }
      }

      context("a version has been pinned to an environment") {
        before {
          clock.incrementBy(Duration.ofHours(1))
          subject.approveVersionFor(manifest, artifact2, version4, environment2.name)
          subject.markAsSuccessfullyDeployedTo(manifest, artifact2, version4, environment2.name)
          subject.approveVersionFor(manifest, artifact2, version5, environment2.name)
          subject.markAsSuccessfullyDeployedTo(manifest, artifact2, version5, environment2.name)
        }

        test("without a pin, latestVersionApprovedIn returns the latest approved version") {
          expectThat(subject.latestVersionApprovedIn(manifest, artifact2, environment2.name))
            .isEqualTo(version5)
            .isNotEqualTo(pin1.version)
        }

        test("latestVersionApprovedIn prefers a pinned version over the latest approved version") {
          subject.pinEnvironment(manifest, pin1)
          expectThat(subject.latestVersionApprovedIn(manifest, artifact2, environment2.name))
            .isEqualTo(version4)
            .isEqualTo(pin1.version)
        }
      }
    }

    context("artifact approval querying") {
      before {
        persist()
        subject.approveVersionFor(manifest, artifact2, version1, environment1.name)
        subject.approveVersionFor(manifest, artifact2, version2, environment1.name)
        subject.approveVersionFor(manifest, artifact2, version3, environment1.name)
      }

      test("we can query for all the versions and know they're approved") {
        expect {
          that(subject.isApprovedFor(manifest, artifact2, version1, environment1.name)).isTrue()
          that(subject.isApprovedFor(manifest, artifact2, version2, environment1.name)).isTrue()
          that(subject.isApprovedFor(manifest, artifact2, version3, environment1.name)).isTrue()
        }
      }
    }

    context("getting all filters by type") {
      before {
        persist()
        subject.store(artifact1, version4, FINAL)
        subject.store(artifact3, version6, FINAL)
      }

      test("querying works") {
        expect {
          that(subject.getAll().size).isEqualTo(3)
          that(subject.getAll(docker).size).isEqualTo(1)
          that(subject.getAll(deb).size).isEqualTo(2)
        }
      }
    }
  }
}
