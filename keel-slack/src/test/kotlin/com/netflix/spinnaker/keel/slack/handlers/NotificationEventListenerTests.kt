package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.NotificationType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.events.PinnedNotification
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.slack.NotificationEventListener
import com.netflix.spinnaker.keel.test.DummyArtifact
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.ZoneId

class NotificationEventListenerTests : JUnit5Minutests {

  class Fixture {
    val repository: KeelRepository = mockk()
    val releaseArtifact = DummyArtifact(reference = "release")
    val version0 = "fnord-1.0.0-h0.a0a0a0a"
    val version1 = "fnord-1.0.1-h1.b1b1b1b"
    val versions = listOf(version0, version1)

    val clock: MutableClock = MutableClock(
      Instant.parse("2020-03-25T00:00:00.00Z"),
      ZoneId.of("UTC")
    )

    val pin = EnvironmentArtifactPin("production", releaseArtifact.reference, version0, "keel@keel.io", "comment")
    val application1 = "fnord1"
    val singleArtifactEnvironments = listOf("test", "staging", "production").associateWith { name ->
      Environment(
        name = name,
        notifications = setOf(
          NotificationConfig(
            type = NotificationType.slack,
            address = "test",
            frequency = NotificationFrequency.verbose
          )
        )
      )
    }

    val singleArtifactDeliveryConfig = DeliveryConfig(
      name = "manifest_$application1",
      application = application1,
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(releaseArtifact),
      environments = singleArtifactEnvironments.values.toSet()
    )

    val pinnedNotification = PinnedNotification(singleArtifactDeliveryConfig, pin)
    val subject = NotificationEventListener(repository, clock, emptyList())


    fun Collection<String>.toArtifactVersions(artifact: DeliveryArtifact) =
      map { PublishedArtifact(artifact.name, artifact.type, it) }
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("pin and unpin notifications") {
      before {
        every {
          repository.environmentNotifications(any(), any())
        } returns setOf(NotificationConfig(NotificationType.slack, "test", NotificationFrequency.verbose))

        every { repository.getArtifactVersion(releaseArtifact, any(), any()) } returns versions.toArtifactVersions(releaseArtifact).first()

        every {
          repository.getArtifact(any(), any())
        } returns releaseArtifact

        every {
          repository.getArtifactVersionByPromotionStatus(any(), any(), any(), any())
        } returns versions.toArtifactVersions(releaseArtifact).first()

        every {
          repository.latestVersionApprovedIn(any(), any(), any())
        } returns versions.last()

      }

//        test("slack notification was sent out") {
//          subject.onPinnedNotification(pinnedNotification)
//          verify { publisher.publishEvent(ofType<SlackPinnedNotification>()) }
//        }
      }
    }
}
