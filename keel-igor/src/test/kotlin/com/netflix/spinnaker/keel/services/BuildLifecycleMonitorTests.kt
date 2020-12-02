package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.config.LifecycleConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.DataSources
import com.netflix.spinnaker.keel.igor.BuildLifecycleMonitor
import com.netflix.spinnaker.keel.igor.artifact.ArtifactMetadataService
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventScope.PRE_DEPLOYMENT
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.ABORTED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.FAILED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.NOT_STARTED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.RUNNING
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.SUCCEEDED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.UNKNOWN
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BUILD
import com.netflix.spinnaker.keel.lifecycle.LifecycleMonitorRepository
import com.netflix.spinnaker.keel.lifecycle.MonitoredTask
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.time.Instant

class BuildLifecycleMonitorTests : JUnit5Minutests {
  internal class Fixture {
    val monitorRepository: LifecycleMonitorRepository = mockk(relaxUnitFun = true)
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    val lifecycleConfig = LifecycleConfig()
    val artifactMetadataService: ArtifactMetadataService = mockk()
    val front50Service: Front50Service = mockk()
    val spinnakerBaseUrl = "www.spin.com"
    val id = "c9bb7369-8433-4014-bcaa-b0ef814234c3"
    val fallbackLink = "www.jenkins.com/my-build"
    val objectMapper = configuredTestObjectMapper()
    val buildNumber = "4"
    val commitId = "56203b1"

    val event = LifecycleEvent(
      type = BUILD,
      scope = PRE_DEPLOYMENT,
      status = NOT_STARTED,
      id = "my-build-1",
      artifactRef = "my-config:my-artifact",
      artifactVersion = "123.4",
      text = "Starting build",
      link = id,
      timestamp = Instant.now(),
      data = mapOf(
        "application" to "my-app",
        "buildNumber" to buildNumber,
        "commitId" to commitId,
        "fallbackLink" to fallbackLink
      )
    )

    val app = Application(
      name = "myapp",
      email = "blah@blah.com",
      dataSources = DataSources(enabled = emptyList(), disabled = listOf("integration"))
    )

    val task = MonitoredTask(
      link = id,
      triggeringEvent = event
    )

    val subject = BuildLifecycleMonitor(
      monitorRepository,
      publisher,
      lifecycleConfig,
      objectMapper,
      artifactMetadataService,
      front50Service,
      spinnakerBaseUrl
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("handles builds") {
      expectThat(subject.handles(BUILD)).isTrue()
    }

    context("updating status") {
      before {
        coEvery { front50Service.applicationByName(any()) } returns app
      }
      context("building") {
        before {
          coEvery { artifactMetadataService.getArtifactMetadata(buildNumber, commitId) } returns getArtifactMetadata("BUILDING")
          runBlocking { subject.monitor(task) }
        }

        test("emits running event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(RUNNING)
        }
      }

      context("success") {
        before {
          coEvery { artifactMetadataService.getArtifactMetadata(buildNumber, commitId) } returns
            getArtifactMetadata("SUCCESS")
          runBlocking { subject.monitor(task) }
        }

        test("emits succeeded event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(SUCCEEDED)
        }
      }

      context("failure") {
        before {
          coEvery { artifactMetadataService.getArtifactMetadata(buildNumber, commitId) } returns
            getArtifactMetadata("FAILURE")
          runBlocking { subject.monitor(task) }
        }

        test("emits failed event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(FAILED)
        }
      }

      context("aborted") {
        before {
          coEvery { artifactMetadataService.getArtifactMetadata(buildNumber, commitId) } returns
            getArtifactMetadata("ABORTED")
          runBlocking { subject.monitor(task) }
        }

        test("emits aborted event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(ABORTED)
        }
      }

      context("UNSTABLE") {
        before {
          coEvery { artifactMetadataService.getArtifactMetadata(buildNumber, commitId) } returns
            getArtifactMetadata("UNSTABLE")
          runBlocking { subject.monitor(task) }
        }

        test("emits succeeded event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(SUCCEEDED)
        }
      }

      context("garbage") {
        before {
          coEvery { artifactMetadataService.getArtifactMetadata(buildNumber, commitId) } returns
            getArtifactMetadata("BLAHHHHHH")
          runBlocking { subject.monitor(task) }
        }

        test("emits unknown event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(UNKNOWN)
        }
      }
    }

    context("handle failure") {
      before {
        coEvery { artifactMetadataService.getArtifactMetadata(buildNumber, commitId) } throws
          RuntimeException("this is embarrassing..")
        coEvery { front50Service.applicationByName(any()) } returns app
      }

      test("failure is marked") {
        runBlocking { subject.monitor(task) }
        verify(exactly = 1) {
          monitorRepository.markFailureGettingStatus(task)
        }
      }

      test("monitoring is ended after max number is hit") {
        val slot = slot<LifecycleEvent>()
        val failingTask = task.copy(numFailures = 4)
        runBlocking { subject.monitor(failingTask) }
        verify(exactly = 1) {
          publisher.publishEvent(capture(slot))
          monitorRepository.delete(failingTask)
        }
        expectThat(slot.captured.status).isEqualTo(UNKNOWN)
      }
    }

    context("handling intermittent failure") {
      before {
        coEvery { artifactMetadataService.getArtifactMetadata(buildNumber, commitId) } returns
          getArtifactMetadata("BUILDING")
          coEvery { front50Service.applicationByName(any()) } returns app
      }
      test("failure count is cleared after a success") {
        val failingTask = task.copy(numFailures = 2)
        runBlocking { subject.monitor(failingTask) }
        verify(exactly = 1) {
          monitorRepository.clearFailuresGettingStatus(failingTask)
        }
      }
    }

    context("choosing link") {
      before {
        coEvery { artifactMetadataService.getArtifactMetadata(buildNumber, commitId) } returns getArtifactMetadata("BUILDING")
      }

      context("integration shut off") {
        before {
          coEvery { front50Service.applicationByName(any()) } returns app
          runBlocking { subject.monitor(task) }
        }

        test("link is jenkins") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.link?.contains("jenkins")).isEqualTo(true)
        }
      }

      context("integration not configured") {
        before {
          coEvery { front50Service.applicationByName(any()) } returns Application(
            name = "myapp",
            email = "blah@blah.com",
            dataSources = DataSources(enabled = emptyList(), disabled = emptyList())
          )
          runBlocking { subject.monitor(task) }
        }

        test("link is jenkins") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.link?.contains("jenkins")).isEqualTo(true)
        }
      }

      context("integration configured") {
        before {
          coEvery { front50Service.applicationByName(any()) } returns Application(
            name = "myapp",
            email = "blah@blah.com",
            dataSources = DataSources(enabled = emptyList(), disabled = emptyList()),
            repoType = "stash",
            repoProjectKey = "project",
            repoSlug = "repo"
          )
          runBlocking { subject.monitor(task) }
        }

        test("link constructed from build id") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.link?.contains(id)).isEqualTo(true)
        }
      }
    }
  }

  private fun getArtifactMetadata(status: String): ArtifactMetadata =
    ArtifactMetadata(
      buildMetadata = BuildMetadata(
        id = 4,
        number = "4",
        uid = "c9bb7369-8433-4014-bcaa-b0ef814234c3",
        status = status
      ),
      gitMetadata = null
    )
}
