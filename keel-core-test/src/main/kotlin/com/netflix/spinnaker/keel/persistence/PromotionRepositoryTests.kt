package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue

abstract class PromotionRepositoryTests<T : PromotionRepository> : JUnit5Minutests {
  abstract fun factory(): T

  open fun Fixture<T>.persist() {}

  open fun T.flush() {}

  data class Fixture<T : PromotionRepository>(
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

    before {
      persist()
    }

    after {
      subject.flush()
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
