package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isFalse
import strikt.assertions.isTrue

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
        expectThrows<NoSuchArtifactException> {
          repository.store(artifact, "1.0")
        }
      }

      test("trying to get versions throws an exception") {
        expectThrows<NoSuchArtifactException> {
          repository.versions(artifact)
        }
      }
    }

    context("the artifact is known") {
      before {
        repository.register(artifact)
      }

      test("re-registering the same artifact raises an exception") {
        expectThrows<ArtifactAlreadyRegistered> {
          repository.register(artifact)
        }
      }

      context("no versions exist") {
        test("listing versions returns an empty list") {
          expectThat(repository.versions(artifact)).isEmpty()
        }
      }

      context("an artifact version already exists") {
        before {
          repository.store(artifact, "1.0")
        }

        test("registering the same version is a no-op") {
          val result = repository.store(artifact, "1.0")
          expectThat(result).isFalse()
          expectThat(repository.versions(artifact)).hasSize(1)
        }
      }

      context("a prior artifact version exists") {
        before {
          repository.store(artifact, "1.0")
        }

        test("the new version is persisted") {
          val result = repository.store(artifact, "2.0")

          expectThat(result).isTrue()
          expectThat(repository.versions(artifact)).containsExactly("2.0", "1.0")
        }
      }
    }
  }
}
