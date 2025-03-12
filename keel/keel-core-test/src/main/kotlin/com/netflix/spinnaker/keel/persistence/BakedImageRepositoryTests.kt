package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.artifacts.BaseLabel.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.BakedImage
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isSuccess
import java.time.Clock

abstract class BakedImageRepositoryTests<T : BakedImageRepository> : JUnit5Minutests {
  abstract fun factory(clock: Clock): T

  val clock = MutableClock()

  open fun T.flush() {}

  val version = "keel-1-h1.9808f2e"
  val amisByRegion = mapOf(
    "us-east-1" to "ami-1",
    "us-west-1" to "ami-2"
  )

  val bakedImage = BakedImage(
    name = "$version-x86_64-20210302221913-bionic-classic-hvm-sriov-ebs",
    baseLabel = RELEASE,
    baseOs = "bionic-classic",
    vmType = "hvm",
    cloudProvider = "aws",
    appVersion = version,
    baseAmiName = "base-ami-123",
    timestamp = clock.instant(),
    amiIdsByRegion = amisByRegion
  )

  val artifact = DebianArtifact(
    name = "keel",
    deliveryConfigName = "keel-config",
    vmOptions = VirtualMachineOptions(
      baseOs = "bionic-classic",
      regions = setOf("us-west-1", "us-east-1")
    )
  )

  data class Fixture<T : BakedImageRepository>(
    val subject: T
  )

  fun tests() = rootContext<Fixture<T>> {
    fixture { Fixture(factory(clock)) }

    after {
      subject.flush()
    }

    context("can store a baked image") {
      test("storing works") {
        expectCatching { subject.store(bakedImage) }
          .isSuccess()
      }

      test("can retrieve stored image") {
        subject.store(bakedImage)
        val image = subject.getByArtifactVersion(version, artifact)
        expectThat(image)
          .isNotNull()
          .get { amiIdsByRegion }
          .isEqualTo(amisByRegion)
      }
    }

    context("no baked image exists") {
      test("getting latest by version does not throw an exception") {
        expectCatching { subject.getByArtifactVersion(version, artifact) }
          .isSuccess()
      }

      test("getting latest by version returns null") {
        expectThat(subject.getByArtifactVersion(version, artifact)).isNull()
      }
    }

    context("updating a baked image if it gets re-baked with a newer base AMI") {
      before {
        subject.store(bakedImage)
      }

      test("the newer AMI details are stored") {
        val newBakedImage  = bakedImage.copy(
          name = "$version-x86_64-20210429221913-bionic-classic-hvm-sriov-ebs",
          baseAmiName = "ami-456",
          amiIdsByRegion = mapOf(
            "us-east-1" to "ami-3",
            "us-west-1" to "ami-4"
          )
        )
        subject.store(newBakedImage)

        expectThat(subject.getByArtifactVersion(version, artifact))
          .isNotNull()
          .get { amiIdsByRegion }
          .isEqualTo(newBakedImage.amiIdsByRegion)
      }
    }
  }

}
