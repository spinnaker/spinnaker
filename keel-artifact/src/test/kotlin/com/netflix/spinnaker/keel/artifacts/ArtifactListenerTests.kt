package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.events.ArtifactPublishedEvent
import com.netflix.spinnaker.keel.api.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.api.support.SpringEventPublisherBridge
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.DockerImage
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import com.netflix.spinnaker.keel.telemetry.ArtifactSaved
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionUpdated
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

internal class ArtifactListenerTests : JUnit5Minutests {
  val publishedDeb = PublishedArtifact(
    type = "DEB",
    customKind = false,
    name = "fnord",
    version = "0.156.0-h58.f67fe09",
    reference = "debian-local:pool/f/fnord/fnord_0.156.0-h58.f67fe09_all.deb",
    metadata = mapOf("releaseStatus" to FINAL),
    provenance = "https://my.jenkins.master/jobs/fnord-release/58"
  )

  val debianArtifact = DebianArtifact(name = "fnord", deliveryConfigName = "fnord-config", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")))
  val dockerArtifact = DockerArtifact(name = "fnord/myimage", tagVersionStrategy = BRANCH_JOB_COMMIT_BY_JOB, deliveryConfigName = "fnord-config")
  val deliveryConfig = DeliveryConfig(name = "fnord-config", application = "fnord", serviceAccount = "keel", artifacts = setOf(debianArtifact, dockerArtifact))

  data class ArtifactFixture(
    val event: ArtifactPublishedEvent,
    val artifact: DeliveryArtifact,
    val repository: KeelRepository = mockk(relaxUnitFun = true),
    val artifactService: ArtifactService = mockk(relaxUnitFun = true),
    val clouddriverService: CloudDriverService = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true),
    val artifactMetadataService: ArtifactMetadataService = mockk(relaxUnitFun = true)
  ) {
    private val eventBridge = SpringEventPublisherBridge(publisher)
    val listener: ArtifactListener = ArtifactListener(repository, publisher, listOf(
      DebianArtifactSupplier(eventBridge, artifactService, artifactMetadataService),
      DockerArtifactSupplier(eventBridge, clouddriverService, artifactMetadataService)
    ))
  }

  fun artifactEventTests() = rootContext<ArtifactFixture> {
    fixture {
      ArtifactFixture(
        event = ArtifactPublishedEvent(
          artifacts = listOf(publishedDeb),
          details = emptyMap()
        ),
        artifact = debianArtifact
      )
    }

    before {
      every { repository.getDeliveryConfig(any()) } returns deliveryConfig
    }

    context("the artifact is not something we're tracking") {
      before {
        every { repository.isRegistered(any(), any()) } returns false
        every { repository.artifactVersions(any()) } returns listOf("0.227.0-h141.bd97556")

        listener.onArtifactPublished(event)
      }

      test("the event is ignored") {
        verify(exactly = 0) { repository.storeArtifact(any(), any(), any(), any()) }
      }

      test("no telemetry is recorded") {
        verify { publisher wasNot Called }
      }
    }

    context("the artifact is registered with versions") {
      before {
        every { repository.isRegistered(artifact.name, artifact.type) } returns true
        every { repository.getArtifact(artifact.name, artifact.type, "fnord-config") } returns listOf(artifact)
        every { repository.artifactVersions(artifact) } returns listOf("0.227.0-h141.bd97556")
      }

      context("the version was already known") {
        before {
          every { repository.storeArtifact(any(), any(), any(), any()) } returns false

          listener.onArtifactPublished(event)
        }

        test("no telemetry is recorded") {
          verify { publisher wasNot Called }
        }
      }

      context("the version is new") {
        before {
          every { repository.storeArtifact(any(), any(), any(), any()) } returns true

          listener.onArtifactPublished(event)
        }

        test("a new artifact version is stored") {
          verify {
            repository.storeArtifact(artifact.name, artifact.type, "fnord-0.156.0-h58.f67fe09", FINAL)
          }
        }

        test("a telemetry event is recorded") {
          verify { publisher.publishEvent(any<ArtifactVersionUpdated>()) }
        }
        test("artifact saved event was sent") {
          verify { publisher.publishEvent(any<ArtifactSaved>()) }
        }
      }
    }
  }

  data class RegisteredFixture(
    val event: ArtifactRegisteredEvent,
    val artifact: DeliveryArtifact,
    val repository: KeelRepository = mockk(relaxUnitFun = true),
    val artifactService: ArtifactService = mockk(relaxUnitFun = true),
    val clouddriverService: CloudDriverService = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true),
    val artifactMetadataService: ArtifactMetadataService = mockk(relaxUnitFun = true)
  ) {
    private val eventBridge = SpringEventPublisherBridge(publisher)
    val listener: ArtifactListener = ArtifactListener(repository, publisher, listOf(
      DebianArtifactSupplier(eventBridge, artifactService, artifactMetadataService),
      DockerArtifactSupplier(eventBridge, clouddriverService, artifactMetadataService)
    ))
  }

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
    }

    context("artifact is already registered") {
      before {
        every { repository.isRegistered("fnord", DEBIAN) } returns true
        every { repository.getArtifact(artifact.name, artifact.type, "fnord-config") } returns listOf(artifact)
        every { repository.artifactVersions(any()) } returns listOf("0.227.0-h141.bd97556")
        listener.onArtifactRegisteredEvent(event)
      }

      test("nothing is done") {
        verify(exactly = 0) { repository.storeArtifact(any(), any(), any()) }
      }
    }

    context("the artifact is not already registered") {
      before {
        every { repository.isRegistered("fnord", DEBIAN) } returns false
      }

      context("there are versions of the artifact") {
        before {
          every { repository.storeArtifact(any(), any(), any(), any()) } returns false
          every { repository.artifactVersions(any()) } returns emptyList()
          coEvery { artifactService.getVersions("fnord", emptyList(), DEBIAN) } returns
            listOf(
              "0.227.0-h141.bd97556",
              "0.226.0-h140.705469b",
              "0.225.0-h139.f5c2ec7",
              "0.224.0-h138.0320b6c"
            )
          coEvery {
            artifactService.getArtifact("fnord", "0.227.0-h141.bd97556", DEBIAN)
          } returns publishedDeb.copy(version = "0.227.0-h141.bd97556")

          listener.onArtifactRegisteredEvent(event)
        }

        test("the newest version is saved") {
          verify(exactly = 1) {
            repository.storeArtifact("fnord", DEBIAN, "fnord-0.227.0-h141.bd97556", FINAL)
          }
        }

        test("artifact saved event was sent") {
          verify { publisher.publishEvent(any<ArtifactSaved>()) }
        }
      }

      context("there no versions of the artifact") {
        before {
          coEvery { artifactService.getVersions("fnord", emptyList(), DEBIAN) } returns listOf()
          coEvery { repository.artifactVersions(any()) } returns listOf()

          listener.onArtifactRegisteredEvent(event)
        }

        test("no versions are persisted") {
          verify(exactly = 0) {
            repository.storeArtifact(any(), any(), any())
          }
        }

        test("artifact saved event was not sent") {
          verify(exactly = 0) { publisher.publishEvent(any<ArtifactSaved>()) }
        }
      }
    }
  }

  val newerPublishedDeb = PublishedArtifact(
    type = "DEB",
    customKind = false,
    name = "fnord",
    version = "0.161.0-h61.116f116",
    reference = "debian-local:pool/f/fnord/fnord_0.161.0-h61.116f116_all.deb",
    metadata = mapOf("releaseStatus" to FINAL),
    provenance = "https://my.jenkins.master/jobs/fnord-release/60"
  )

  data class SyncArtifactsFixture(
    val debArtifact: DeliveryArtifact,
    val dockerArtifact: DockerArtifact,
    val repository: KeelRepository = mockk(relaxUnitFun = true),
    val artifactService: ArtifactService = mockk(relaxUnitFun = true),
    val clouddriverService: CloudDriverService = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true),
    val artifactMetadataService: ArtifactMetadataService = mockk(relaxUnitFun = true)
  ) {
    private val eventBridge = SpringEventPublisherBridge(publisher)
    val listener: ArtifactListener = ArtifactListener(repository, publisher, listOf(
      DebianArtifactSupplier(eventBridge, artifactService, artifactMetadataService),
      DockerArtifactSupplier(eventBridge, clouddriverService, artifactMetadataService)
    ))
  }

  fun syncArtifactsFixture() = rootContext<SyncArtifactsFixture> {
    fixture {
      SyncArtifactsFixture(
        debArtifact = debianArtifact,
        dockerArtifact = dockerArtifact
      )
    }

    before {
      every { repository.getDeliveryConfig(any()) } returns deliveryConfig
      listener.onApplicationUp()
    }

    context("we don't have any versions of an artifact") {
      before {
        every { repository.getAllArtifacts() } returns listOf(debArtifact, dockerArtifact)
        every { repository.artifactVersions(debArtifact) } returns listOf()
        every { repository.artifactVersions(dockerArtifact) } returns listOf()
        coEvery { artifactService.getVersions(debArtifact.name, emptyList(), DEBIAN) } returns listOf("0.161.0-h61.116f116", "0.160.0-h60.f67f671")
        coEvery { clouddriverService.findDockerTagsForImage("*", dockerArtifact.name, any()) } returns listOf("master-h5.blahblah")
        coEvery {
          clouddriverService.findDockerImages("*", dockerArtifact.name, "master-h5.blahblah", any(), any())
        } returns listOf(DockerImage("test", dockerArtifact.name, "master-h5.blahblah", "abcd1234"))
        coEvery { artifactService.getArtifact(debArtifact.name, "0.161.0-h61.116f116", DEBIAN) } returns newerPublishedDeb
        every { repository.storeArtifact(debArtifact.name, debArtifact.type, "${debArtifact.name}-0.161.0-h61.116f116", FINAL) } returns true
        every { repository.storeArtifact(dockerArtifact.name, dockerArtifact.type, "master-h5.blahblah", null) } returns true
      }

      test("new version is stored") {
        listener.syncArtifactVersions()
        verify { repository.storeArtifact(debArtifact.name, debArtifact.type, "${debArtifact.name}-0.161.0-h61.116f116", FINAL) }
        verify { repository.storeArtifact(dockerArtifact.name, dockerArtifact.type, "master-h5.blahblah", null) }
        verify { publisher.publishEvent(any<ArtifactSaved>()) }
      }
    }

    context("there is one artifact with one version stored") {
      before {
        every { repository.getAllArtifacts() } returns listOf(debArtifact, dockerArtifact)
        every { repository.artifactVersions(debArtifact) } returns listOf("${debArtifact.name}-0.156.0-h58.f67fe09")
        every { repository.artifactVersions(dockerArtifact) } returns listOf("master-h5.blahblah")
      }

      context("a new version") {
        before {
          coEvery { artifactService.getVersions(debArtifact.name, emptyList(), DEBIAN) } returns listOf("0.161.0-h61.116f116", "0.160.0-h60.f67f671")
          coEvery { clouddriverService.findDockerTagsForImage("*", dockerArtifact.name, any()) } returns listOf("master-h6.hehehe")
          coEvery {
            clouddriverService.findDockerImages("*", dockerArtifact.name, "master-h6.hehehe", any(), any())
          } returns listOf(DockerImage("test", dockerArtifact.name, "master-h6.hehehe", "abcd1234"))
          coEvery { artifactService.getArtifact(debArtifact.name, "0.161.0-h61.116f116", DEBIAN) } returns newerPublishedDeb
          every { repository.storeArtifact(debArtifact.name, debArtifact.type, "${debArtifact.name}-0.161.0-h61.116f116", FINAL) } returns true
          every { repository.storeArtifact(dockerArtifact.name, dockerArtifact.type, "master-h6.hehehe", null) } returns true
        }

        test("new version stored") {
          listener.syncArtifactVersions()
          verify { repository.storeArtifact(debArtifact.name, debArtifact.type, "${debArtifact.name}-0.161.0-h61.116f116", FINAL) }
          verify { repository.storeArtifact(dockerArtifact.name, dockerArtifact.type, "master-h6.hehehe", null) }
          verify { publisher.publishEvent(any<ArtifactSaved>()) }
        }
      }

      context("no new version") {
        before {
          coEvery { artifactService.getVersions(debArtifact.name, emptyList(), DEBIAN) } returns listOf("0.156.0-h58.f67fe09")
          coEvery {
            artifactService.getArtifact(debArtifact.name, "0.156.0-h58.f67fe09", DEBIAN)
          } returns PublishedArtifact(name = debArtifact.name, type = DEBIAN, reference = debArtifact.name, version = "0.156.0-h58.f67fe09")
          coEvery { clouddriverService.findDockerTagsForImage("*", dockerArtifact.name, any()) } returns listOf("master-h5.blahblah")
          coEvery {
            clouddriverService.findDockerImages("*", dockerArtifact.name, "master-h5.blahblah", any(), any())
          } returns listOf(DockerImage("test", dockerArtifact.name, "master-h5.blahblah", "abcd1234"))
        }

        test("store not called") {
          listener.syncArtifactVersions()
          verify(exactly = 0) { repository.storeArtifact(debArtifact.name, debArtifact.type, any(), FINAL) }
          verify(exactly = 0) { repository.storeArtifact(dockerArtifact.name, dockerArtifact.type, any(), FINAL) }
          verify(exactly = 0) { publisher.publishEvent(any<ArtifactSaved>()) }

        }
      }

      context("no version information ") {
        before {
          coEvery { artifactService.getVersions(debArtifact.name, emptyList(), DEBIAN) } returns listOf()
          coEvery { clouddriverService.findDockerTagsForImage("*", dockerArtifact.name, any()) } returns listOf()
        }

        test("store not called") {
          listener.syncArtifactVersions()
          verify(exactly = 0) { repository.storeArtifact(debArtifact.name, debArtifact.type, any(), FINAL) }
          verify(exactly = 0) { repository.storeArtifact(dockerArtifact.name, dockerArtifact.type, any(), FINAL) }
          verify(exactly = 0) { publisher.publishEvent(any<ArtifactSaved>()) }
        }
      }
    }
  }
}
