package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.net.URI

abstract class ArtifactRepositoryTests<T : ArtifactRepository> : JUnit5Minutests {
  abstract fun factory(): T

  open fun flush() {}

  data class Fixture<T : ArtifactRepository>(
    val artifact: DeliveryArtifact,
    val repository: T
  )

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(
        artifact = DeliveryArtifact("fnord", DEB),
        repository = factory()
      )
    }

    after {
      flush()
    }

    context("the artifact is unknown") {
      test("the artifact is not registered") {
        expectThat(repository.isRegistered(artifact.name, artifact.type)).isFalse()
      }

      test("registering a new version throws an exception") {
        expectThrows<IllegalArgumentException> {
          repository.store(
            DeliveryArtifactVersion(
              artifact,
              "1.0",
              URI("https://my.jenkins.master/job/${artifact.name}-release/1")
            )
          )
        }
      }
    }

    context("the artifact is known") {

      before {
        repository.register(artifact)
      }

      context("the artifact version already exists") {
        before {
          repository.store(
            DeliveryArtifactVersion(
              artifact,
              "1.0",
              URI("https://my.jenkins.master/job/${artifact.name}-release/1")
            )
          )
        }

        test("registering the same version is a no-op") {
          val result = repository.store(
            DeliveryArtifactVersion(
              artifact,
              "1.0",
              URI("https://my.jenkins.master/job/${artifact.name}-release/1")
            )
          )
          expectThat(result).isFalse()
          expectThat(repository.versions(artifact)).hasSize(1)
        }
      }

      context("a prior artifact version exists") {
        before {
          repository.store(
            DeliveryArtifactVersion(
              artifact,
              "1.0",
              URI("https://my.jenkins.master/job/${artifact.name}-release/1")
            )
          )
        }

        test("the new version is persisted") {
          val result = repository.store(
            DeliveryArtifactVersion(
              artifact,
              "2.0",
              URI("https://my.jenkins.master/job/${artifact.name}-release/2")
            )
          )

          expectThat(result).isTrue()
          expectThat(repository.versions(artifact)) {
            hasSize(2)
            first().version.isEqualTo("2.0")
            second().version.isEqualTo("1.0")
          }
        }
      }
    }
  }
}

private fun <T : Iterable<E>, E> Assertion.Builder<T>.second(): Assertion.Builder<E> =
  get { toList()[1] }

private val Assertion.Builder<DeliveryArtifactVersion>.version: Assertion.Builder<String>
  get() = get { version }
