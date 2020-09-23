package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.time.Clock

/**
 * In the artifact repository we have several methods that generate summary views of data
 * that are returned in the /application/{application} endpoint.
 * This class tests some of that data generation.
 */
abstract class ApplicationSummaryGenerationTests<T : ArtifactRepository> : JUnit5Minutests {

  abstract fun factory(clock: Clock): T

  val clock = MutableClock()

  open fun T.flush() {}

  data class Fixture<T : ArtifactRepository>(
    val subject: T
  ) {
    val artifact = DebianArtifact(
      name = "keeldemo",
      deliveryConfigName = "my-manifest",
      reference = "my-artifact",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      statuses = setOf(RELEASE)
    )

    val environmentA = Environment("aa")
    val environmentB = Environment(
      name = "bb",
      constraints = setOf(DependsOnConstraint("test"), ManualJudgementConstraint())
    )
    val manifest = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(environmentA, environmentB)
    )
    val version1 = "keeldemo-1.0.1-h11.1a1a1a1" // release
    val version2 = "keeldemo-1.0.2-h12.2b2b2b2" // release
  }

  open fun Fixture<T>.persist() {
    with(subject) {
      register(artifact)
      setOf(version1, version2).forEach {
        storeVersion(artifact.toPublishedArtifact(it, RELEASE))
      }
    }
    persist(manifest)
  }

  abstract fun persist(manifest: DeliveryConfig)

  fun tests() = rootContext<Fixture<T>> {
    fixture { Fixture(factory(clock)) }

    before {
      persist()
    }

    after {
      subject.flush()
    }

    context("artifact 1 skipped in envA, manual judgement before envB") {
      before {
        // version 1 and 2 are approved in env A
        subject.approveVersionFor(manifest, artifact, version1, environmentA.name)
        subject.approveVersionFor(manifest, artifact, version2, environmentA.name)
        // only version 2 is approved in env B
        subject.approveVersionFor(manifest, artifact, version2, environmentB.name)
        // version 1 has been skipped in env A by version 2
        subject.markAsSkipped(manifest, artifact, version1, environmentA.name, version2)
        // version 2 was successfully deployed to both envs
        subject.markAsSuccessfullyDeployedTo(manifest, artifact, version2, environmentA.name)
        subject.markAsSuccessfullyDeployedTo(manifest, artifact, version2, environmentB.name)
      }

      test("skipped versions don't get a pending status in the next env") {
        val envSummaries = subject.getEnvironmentSummaries(manifest).sortedBy { it.name }
        expect {
          that(envSummaries.size).isEqualTo(2)
          that(envSummaries[0].artifacts.first().versions.current).isEqualTo(version2)
          that(envSummaries[0].artifacts.first().versions.pending).isEmpty()
          that(envSummaries[0].artifacts.first().versions.skipped).containsExactly(version1)
          that(envSummaries[1].artifacts.first().versions.current).isEqualTo(version2)
          that(envSummaries[1].artifacts.first().versions.pending).isEmpty()
          that(envSummaries[1].artifacts.first().versions.skipped).containsExactly(version1)
        }
      }
    }
  }
}
