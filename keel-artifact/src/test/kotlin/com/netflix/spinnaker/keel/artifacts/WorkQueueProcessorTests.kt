package com.netflix.spinnaker.keel.artifacts

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.config.WorkProcessingConfig
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus
import com.netflix.spinnaker.keel.persistence.WorkQueueRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionUpdated
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Clock

internal class WorkQueueProcessorTests : JUnit5Minutests {

  val publishedDeb = PublishedArtifact(
    type = "DEB",
    customKind = false,
    name = "fnord",
    version = "0.156.0-h58.f67fe09",
    reference = "debian-local:pool/f/fnord/fnord_0.156.0-h58.f67fe09_all.deb",
    metadata = mapOf("releaseStatus" to ArtifactStatus.FINAL, "buildNumber" to "58", "commitId" to "f67fe09"),
    provenance = "https://my.jenkins.master/jobs/fnord-release/58",
    buildMetadata = BuildMetadata(
      id = 58,
      number = "58",
      status = "BUILDING",
      uid = "i-am-a-uid-obviously"
    )
  ).normalized()

  val newerPublishedDeb = PublishedArtifact(
    type = "DEB",
    customKind = false,
    name = "fnord",
    version = "0.161.0-h61.116f116",
    reference = "debian-local:pool/f/fnord/fnord_0.161.0-h61.116f116_all.deb",
    metadata = mapOf("releaseStatus" to ArtifactStatus.FINAL, "buildNumber" to "61", "commitId" to "116f116"),
    provenance = "https://my.jenkins.master/jobs/fnord-release/60",
    buildMetadata = BuildMetadata(
      id = 58,
      number = "58",
      status = "BUILDING",
      uid = "just-a-uid-obviously"
    )
  ).normalized()

  val publishedDocker = PublishedArtifact(
    type = DOCKER,
    customKind = false,
    name = "fnord/myimage",
    version = "master-h5.blahblah",
    reference = "fnord"
  )

  val newerPublishedDocker = publishedDocker.copy(version = "master-h6.hehehe")

  val debianArtifact = DebianArtifact(name = "fnord", deliveryConfigName = "fnord-config", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")))
  val dockerArtifact = DockerArtifact(name = "fnord/myimage", tagVersionStrategy = TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB, deliveryConfigName = "fnord-config")
  val deliveryConfig = DeliveryConfig(name = "fnord-config", application = "fnord", serviceAccount = "keel", artifacts = setOf(debianArtifact, dockerArtifact))

  val artifactMetadata = ArtifactMetadata(
    gitMetadata = GitMetadata(commit = "f00baah", author = "joesmith", branch = "master"),
    buildMetadata = BuildMetadata(id = 1, status = "SUCCEEDED")
  )

  val artifactVersion = slot<PublishedArtifact>()

  abstract class EventQueueProcessorFixture {
    val repository: KeelRepository = mockk(relaxUnitFun = true)
    val workQueueRepository: WorkQueueRepository = mockk(relaxUnitFun = true)
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    val dockerArtifactSupplier: DockerArtifactSupplier = mockk(relaxUnitFun = true) {
      coEvery { shouldProcessArtifact(any()) } returns true
      coEvery { supportedArtifact } returns SupportedArtifact(DOCKER, DockerArtifact::class.java)
    }
    val debianArtifactSupplier: DebianArtifactSupplier = mockk(relaxUnitFun = true) {
      coEvery { shouldProcessArtifact(any()) } returns true
      coEvery { supportedArtifact } returns SupportedArtifact(DEBIAN, DebianArtifact::class.java)
    }
    val artifactSuppliers = listOf(dockerArtifactSupplier, debianArtifactSupplier)
    val spectator = NoopRegistry()
    val clock: Clock = MutableClock()
    val springEnv: Environment = mockk(relaxed = true)

    val subject = WorkQueueProcessor(
      config = WorkProcessingConfig(),
      workQueueRepository = workQueueRepository,
      repository = repository,
      artifactSuppliers = artifactSuppliers,
      publisher = publisher,
      spectator = spectator,
      clock = clock,
      springEnv = springEnv
    )
  }

  data class ArtifactPublishedFixture(
    val version: PublishedArtifact,
    val artifact: DeliveryArtifact
  ) : EventQueueProcessorFixture()

  fun artifactEventTests() = rootContext<ArtifactPublishedFixture> {
    fixture {
      ArtifactPublishedFixture(
        version = publishedDeb,
        artifact = debianArtifact
      )
    }

    before {
      every { repository.getDeliveryConfig(any()) } returns deliveryConfig
      every { debianArtifactSupplier.supportedArtifact } returns SupportedArtifact(DEBIAN, DebianArtifact::class.java)
      every { dockerArtifactSupplier.supportedArtifact } returns SupportedArtifact(DOCKER, DockerArtifact::class.java)
    }

    context("the artifact is not something we're tracking") {
      before {
        every { repository.isRegistered(any(), any()) } returns false
        subject.handlePublishedArtifact(version)
      }

      test("the event is ignored") {
        coVerify(exactly = 0) { repository.storeArtifactVersion(any()) }
      }

      test("no telemetry is recorded") {
        coVerify { publisher wasNot Called }
      }
    }

    context("the artifact is registered with versions") {
      before {
        every { repository.isRegistered(artifact.name, artifact.type) } returns true
        every {
          debianArtifactSupplier.getLatestArtifact(deliveryConfig, artifact)
        } returns publishedDeb
        coEvery {
          debianArtifactSupplier.getArtifactMetadata(publishedDeb)
        } returns artifactMetadata
        every {
          debianArtifactSupplier.shouldProcessArtifact(any())
        } returns true
      }

      context("the version was already known") {
        before {
          every { repository.storeArtifactVersion(any()) } returns false
          every { repository.getAllArtifacts(DEBIAN, any()) } returns listOf(debianArtifact)

          subject.handlePublishedArtifact(version)
        }

        test("only lifecycle event recorded") {
          coVerify(exactly = 1) { publisher.publishEvent(ofType<LifecycleEvent>()) }
          coVerify(exactly = 0) { publisher.publishEvent(ofType<ArtifactVersionUpdated>())  }
        }
      }

      context("the version is new") {
        before {
          coEvery {
            debianArtifactSupplier.getArtifactMetadata(newerPublishedDeb)
          } returns artifactMetadata

          every { repository.storeArtifactVersion(any()) } returns true
          every { repository.getAllArtifacts(DEBIAN, any()) } returns listOf(debianArtifact)

          subject.handlePublishedArtifact(newerPublishedDeb)
        }

        test("a new artifact version is stored") {
          val slot = slot<PublishedArtifact>()
          coVerify {repository.storeArtifactVersion(capture(artifactVersion)) }

          with(artifactVersion.captured) {
            expectThat(name).isEqualTo(artifact.name)
            expectThat(type).isEqualTo(artifact.type)
            expectThat(version).isEqualTo(newerPublishedDeb.version)
            expectThat(status).isEqualTo(ArtifactStatus.FINAL)
          }
        }

        test("artifact metadata is added before storing") {
          coVerify(exactly = 1) {
            debianArtifactSupplier.getArtifactMetadata(newerPublishedDeb)
          }

          with(artifactVersion.captured) {
            expectThat(gitMetadata).isEqualTo(artifactMetadata.gitMetadata)
            expectThat(buildMetadata).isEqualTo(artifactMetadata.buildMetadata)
          }
        }
      }
    }
  }


  data class LifecycleEventsFixture(
    val debArtifact: DeliveryArtifact,
    val dockerArtifact: DockerArtifact
  ) : EventQueueProcessorFixture()

  fun lifecyclePublishingEventTests() = rootContext<LifecycleEventsFixture> {
    fixture {
      LifecycleEventsFixture(
        debArtifact = debianArtifact,
        dockerArtifact = dockerArtifact
      )
    }

    before {
      every { repository.getDeliveryConfig(any()) } returns deliveryConfig
      every { debianArtifactSupplier.supportedArtifact } returns SupportedArtifact(DEBIAN, DebianArtifact::class.java)
      every { dockerArtifactSupplier.supportedArtifact } returns SupportedArtifact(DOCKER, DockerArtifact::class.java)
      coEvery { debianArtifactSupplier.getArtifactMetadata(any()) } returns artifactMetadata
      coEvery { dockerArtifactSupplier.getArtifactMetadata(any()) } returns artifactMetadata
      coEvery { repository.storeArtifactVersion(any()) } returns true
      subject.onApplicationUp()
    }

    context("lifecycle events") {
      context("multiple artifacts") {
        before {
          every { repository.getAllArtifacts(DEBIAN, any()) } returns
            listOf(
              debianArtifact,
              debianArtifact.copy(reference = "blah-blay", deliveryConfigName = "another-config")
            )
          subject.publishBuildLifecycleEvent(publishedDeb)
        }

        test("publishes event for each artifact") {
          coVerify(exactly = 2) { publisher.publishEvent(ofType<LifecycleEvent>()) }
        }
      }

      context("single artifact") {
        before {
          coEvery { repository.getAllArtifacts(DEBIAN, any()) } returns
            listOf(debianArtifact)
          subject.publishBuildLifecycleEvent(publishedDeb)
        }

        test("publishes event with monitor = true") {
          val slot = slot<LifecycleEvent>()
          coVerify(exactly = 1) { publisher.publishEvent(capture(slot)) }
          expectThat(slot.captured.status).isEqualTo(LifecycleEventStatus.RUNNING)
          expectThat(slot.captured.startMonitoring).isEqualTo(true)
        }
      }
    }
  }
}
