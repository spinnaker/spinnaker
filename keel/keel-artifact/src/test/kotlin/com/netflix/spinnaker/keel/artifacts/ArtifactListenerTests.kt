package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.config.ArtifactConfig
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.config.ArtifactRefreshConfig
import com.netflix.spinnaker.keel.persistence.KeelRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

internal class ArtifactListenerTests : JUnit5Minutests {
  val publishedDeb = PublishedArtifact(
    type = "DEB",
    customKind = false,
    name = "fnord",
    version = "0.156.0-h58.f67fe09",
    reference = "debian-local:pool/f/fnord/fnord_0.156.0-h58.f67fe09_all.deb",
    metadata = mapOf("releaseStatus" to FINAL, "buildNumber" to "58", "commitId" to "f67fe09"),
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
    metadata = mapOf("releaseStatus" to FINAL, "buildNumber" to "61", "commitId" to "116f116"),
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
  val dockerArtifact = DockerArtifact(name = "fnord/myimage", tagVersionStrategy = BRANCH_JOB_COMMIT_BY_JOB, deliveryConfigName = "fnord-config")
  val deliveryConfig = DeliveryConfig(name = "fnord-config", application = "fnord", serviceAccount = "keel", artifacts = setOf(debianArtifact, dockerArtifact))

  abstract class ArtifactListenerFixture {
    val repository: KeelRepository = mockk(relaxUnitFun = true)
    val dockerArtifactSupplier: DockerArtifactSupplier = mockk(relaxUnitFun = true) {
      every { shouldProcessArtifact(any()) } returns true
    }
    val debianArtifactSupplier: DebianArtifactSupplier = mockk(relaxUnitFun = true) {
      every { shouldProcessArtifact(any()) } returns true
    }
    val artifactConfig = ArtifactConfig()
    val refreshConfig = ArtifactRefreshConfig()
    val workQueueProcessor: WorkQueueProcessor = mockk() {
      every { enrichAndStore(any(), any()) } returns false
    }
    val listener: ArtifactListener = ArtifactListener(
      repository,
      listOf(debianArtifactSupplier, dockerArtifactSupplier),
      artifactConfig,
      refreshConfig,
      workQueueProcessor
    )
  }

  data class RegisteredFixture(
    val event: ArtifactRegisteredEvent,
    val artifact: DeliveryArtifact
  ) : ArtifactListenerFixture()

  fun artifactRegisteredEventTests() = rootContext<RegisteredFixture> {
    fixture {
      DebianArtifact(
        name = "fnord",
        vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
        deliveryConfigName = "fnord-config"
      ).let {
        RegisteredFixture(
          event = ArtifactRegisteredEvent(it),
          artifact = it
        )
      }
    }

    before {
      every { repository.getDeliveryConfig(any()) } returns deliveryConfig
      every { debianArtifactSupplier.supportedArtifact } returns SupportedArtifact(DEBIAN, DebianArtifact::class.java)
      every { dockerArtifactSupplier.supportedArtifact } returns SupportedArtifact(DOCKER, DockerArtifact::class.java)
    }

    context("artifact already has saved versions") {
      before {
        every { repository.artifactVersions(event.artifact, any()) } returns listOf(publishedDeb)
        listener.onArtifactRegisteredEvent(event)
      }

      test("nothing is done") {
        coVerify(exactly = 0) { workQueueProcessor.enrichAndStore(any(), any()) }
      }
    }

    context("the artifact does not have any versions stored") {
      before {
        every { repository.artifactVersions(event.artifact, any()) } returns emptyList()
      }

      context("there are available versions of the artifact") {
        before {
          every { repository.storeArtifactVersion(any()) } returns false
          every {
            debianArtifactSupplier.getLatestArtifact(deliveryConfig, artifact)
          } returns publishedDeb

          every { repository.getAllArtifacts(DEBIAN, any()) } returns listOf(debianArtifact)

          listener.onArtifactRegisteredEvent(event)
        }

        test("the newest version is saved") {
          coVerify(exactly = 1) {
            workQueueProcessor.enrichAndStore(publishedDeb, any())
          }
        }
      }

      context("there are no versions of the artifact available") {
        before {
          every {
            debianArtifactSupplier.getLatestArtifact(deliveryConfig, artifact)
          } returns null

          listener.onArtifactRegisteredEvent(event)
        }

        test("no versions are persisted") {
          coVerify(exactly = 0) {
            repository.storeArtifactVersion(any())
          }
        }
      }
    }
  }

  data class SyncArtifactsFixture(
    val debArtifact: DeliveryArtifact,
    val dockerArtifact: DockerArtifact
  ) : ArtifactListenerFixture()

  fun syncArtifactsFixture() = rootContext<SyncArtifactsFixture> {
    fixture {
      SyncArtifactsFixture(
        debArtifact = debianArtifact,
        dockerArtifact = dockerArtifact
      )
    }

    before {
      every { repository.getDeliveryConfig(any()) } returns deliveryConfig
      every { debianArtifactSupplier.supportedArtifact } returns SupportedArtifact(DEBIAN, DebianArtifact::class.java)
      every { dockerArtifactSupplier.supportedArtifact } returns SupportedArtifact(DOCKER, DockerArtifact::class.java)
      every { repository.storeArtifactVersion(any()) } returns true
      listener.onApplicationUp()
    }

    context("we don't have any versions of the artifacts") {
      before {
        every { repository.getAllArtifacts() } returns listOf(debArtifact, dockerArtifact)
        every { repository.artifactVersions(debArtifact, any()) } returns emptyList()
        every { repository.artifactVersions(dockerArtifact, any()) } returns emptyList()
      }

      context("versions are available") {
        before {
          every {
            debianArtifactSupplier.getLatestArtifacts(deliveryConfig, debArtifact, 1)
          } returns listOf(publishedDeb)

          every {
            dockerArtifactSupplier.getLatestArtifacts(deliveryConfig, dockerArtifact, 1)
          } returns listOf(publishedDocker)

          every { repository.getAllArtifacts(DEBIAN, any()) } returns listOf(debianArtifact)
          every { repository.getAllArtifacts(DOCKER, any()) } returns listOf(dockerArtifact)
        }

        test("latest versions are stored") {
          listener.syncLastLimitArtifactVersions()

          val artifactVersions = mutableListOf<PublishedArtifact>()

          coVerify(exactly = 2) {
            workQueueProcessor.enrichAndStore(any(), any())
          }
        }
      }
    }

    context("there are artifacts with versions stored") {
      before {
        every { repository.getAllArtifacts() } returns listOf(debArtifact, dockerArtifact)
        every { repository.artifactVersions(debArtifact, any()) } returns listOf(publishedDeb)
        every { repository.artifactVersions(dockerArtifact, any()) } returns listOf(publishedDocker)
      }

      context("no newer versions are available") {
        before {
          every {
            debianArtifactSupplier.getLatestArtifacts(deliveryConfig, debArtifact, 1)
          } returns listOf(publishedDeb)

          every {
            dockerArtifactSupplier.getLatestArtifacts(deliveryConfig, dockerArtifact, 1)
          } returns listOf(publishedDocker)
        }

        test("store not called") {
          listener.syncLastLimitArtifactVersions()
          coVerify(exactly = 0) { workQueueProcessor.enrichAndStore(any(), any()) }
        }
      }

      context("newer versions are available") {
        before {
          every {
            debianArtifactSupplier.getLatestArtifacts(deliveryConfig, debArtifact, 1)
          } returns listOf(newerPublishedDeb)

          every {
            dockerArtifactSupplier.getLatestArtifacts(deliveryConfig, dockerArtifact, 1)
          } returns listOf(newerPublishedDocker)

          every { repository.getAllArtifacts(DEBIAN, any()) } returns listOf(debianArtifact)
          every { repository.getAllArtifacts(DOCKER, any()) } returns listOf(dockerArtifact)
        }

        test("new versions are stored") {
          listener.syncLastLimitArtifactVersions()

          coVerify(exactly = 2) {
            workQueueProcessor.enrichAndStore(any(), any())
          }
        }
      }
    }
  }
}
