package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue
import strikt.assertions.succeeded

abstract class ArtifactRepositoryTests<T : ArtifactRepository> : JUnit5Minutests {
  abstract fun factory(): T

  open fun Fixture<T>.persist() {}

  open fun T.flush() {}

  data class Fixture<T : ArtifactRepository>(
    val subject: T
  ) {
    val artifact1 = DeliveryArtifact("foo", DEB)
    val artifact2 = DeliveryArtifact("bar", DEB)
    val environment1 = Environment("test")
    val environment2 = Environment("staging")
    val manifest = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      artifacts = setOf(artifact1, artifact2),
      environments = setOf(environment1, environment2)
    )
    val version1_0 = "1.0"
    val version1_1 = "1.1"
    val version1_2 = "1.2"
  }

  fun tests() = rootContext<Fixture<T>> {
    fixture { Fixture(factory()) }

    after {
      subject.flush()
    }

    context("the artifact is unknown") {
      test("the artifact is not registered") {
        expectThat(subject.isRegistered(artifact1.name, artifact1.type)).isFalse()
      }

      test("registering a new version throws an exception") {
        expectThrows<NoSuchArtifactException> {
          subject.store(artifact1, version1_0)
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

      test("re-registering the same artifact raises an exception") {
        expectThrows<ArtifactAlreadyRegistered> {
          subject.register(artifact1)
        }
      }

      context("no versions exist") {
        test("listing versions returns an empty list") {
          expectThat(subject.versions(artifact1)).isEmpty()
        }
      }

      context("an artifact version already exists") {
        before {
          subject.store(artifact1, version1_0)
        }

        test("registering the same version is a no-op") {
          val result = subject.store(artifact1, version1_0)
          expectThat(result).isFalse()
          expectThat(subject.versions(artifact1)).hasSize(1)
        }

        test("registering a new version adds it to the list") {
          val result = subject.store(artifact1, version1_1)

          expectThat(result).isTrue()
          expectThat(subject.versions(artifact1)).containsExactly(version1_1, version1_0)
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
          setOf(version1_0, version1_1, version1_2).forEach {
            expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, it, environment1.name))
              .isFalse()
          }
        }
      }

      context("a version has been promoted to an environment") {
        before {
          subject.approveVersionFor(manifest, artifact1, version1_0, environment1.name)
        }

        test("the approved version for that environment matches") {
          expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment1.name))
            .isEqualTo(version1_0)
        }

        test("the version is not considered successfully deployed yet") {
          expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, version1_0, environment1.name))
            .isFalse()
        }

        test("promoting the same version again returns false") {
          expectCatching {
            subject.approveVersionFor(manifest, artifact1, version1_0, environment1.name)
          }
            .succeeded()
            .isFalse()
        }

        test("promoting a new version returns true") {
          expectCatching {
            subject.approveVersionFor(manifest, artifact1, version1_1, environment1.name)
          }
            .succeeded()
            .isTrue()
        }

        context("the version is marked as successfully deployed") {
          before {
            subject.markAsSuccessfullyDeployedTo(manifest, artifact1, version1_0, environment1.name)
          }

          test("the version is now considered successfully deployed") {
            expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, version1_0, environment1.name))
              .isTrue()
          }

          context("a new version is promoted to the same environment") {
            before {
              subject.approveVersionFor(manifest, artifact1, version1_1, environment1.name)
            }

            test("the latest approved version changes") {
              expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment1.name))
                .isEqualTo(version1_1)
            }

            test("the version is not considered successfully deployed yet") {
              expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, version1_1, environment1.name))
                .isFalse()
            }

            context("the new version is marked as successfully deployed") {
              before {
                subject.markAsSuccessfullyDeployedTo(manifest, artifact1, version1_1, environment1.name)
              }

              test("the old version is still considered successfully deployed") {
                expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, version1_0, environment1.name))
                  .isTrue()
              }

              test("the new version is also considered successfully deployed") {
                expectThat(subject.wasSuccessfullyDeployedTo(manifest, artifact1, version1_1, environment1.name))
                  .isTrue()
              }
            }
          }
        }

        context("a version of a different artifact is promoted to the environment") {
          before {
            subject.approveVersionFor(manifest, artifact2, version1_2, environment1.name)
          }

          test("the approved version of the original artifact remains the same") {
            expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment1.name))
              .isEqualTo(version1_0)
          }

          test("the approved version of the new artifact matches") {
            expectThat(subject.latestVersionApprovedIn(manifest, artifact2, environment1.name))
              .isEqualTo(version1_2)
          }
        }

        context("a different version of the same artifact is promoted to another environment") {
          before {
            subject.approveVersionFor(manifest, artifact1, version1_1, environment2.name)
          }

          test("the approved version in the original environment is unaffected") {
            expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment1.name))
              .isEqualTo(version1_0)
          }

          test("the approved version in the new environment matches") {
            expectThat(subject.latestVersionApprovedIn(manifest, artifact1, environment2.name))
              .isEqualTo(version1_1)
          }
        }
      }
    }
  }
}
