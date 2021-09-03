package com.netflix.spinnaker.keel.notifications

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.NotificationType
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.events.ApplicationActuationPaused
import com.netflix.spinnaker.keel.events.ArtifactDeployedNotification
import com.netflix.spinnaker.keel.events.PinnedNotification
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventScope
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType
import com.netflix.spinnaker.keel.network.NetworkEndpoint
import com.netflix.spinnaker.keel.network.NetworkEndpointProvider
import com.netflix.spinnaker.keel.network.NetworkEndpointType.DNS
import com.netflix.spinnaker.keel.notifications.scm.ScmNotifier
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus.FAILED
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus.SUCCEEDED
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.notifications.slack.SlackService
import com.netflix.spinnaker.keel.notifications.slack.handlers.ArtifactDeploymentNotificationHandler
import com.netflix.spinnaker.keel.notifications.slack.handlers.GitDataGenerator
import com.netflix.spinnaker.keel.notifications.slack.handlers.LifecycleEventNotificationHandler
import com.netflix.spinnaker.keel.notifications.slack.handlers.PausedNotificationHandler
import com.netflix.spinnaker.keel.notifications.slack.handlers.PinnedNotificationHandler
import com.netflix.spinnaker.keel.notifications.slack.handlers.UnpinnedNotificationHandler
import com.netflix.spinnaker.keel.notifications.slack.handlers.VerificationCompletedNotificationHandler
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionVetoed
import com.netflix.spinnaker.keel.telemetry.VerificationCompleted
import com.netflix.spinnaker.keel.test.DummyArtifact
import com.netflix.spinnaker.keel.test.DummyArtifactReferenceResourceSpec
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import com.slack.api.model.kotlin_extension.block.SectionBlockBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery as every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import strikt.api.expectThat
import strikt.assertions.contains
import java.time.Instant
import java.time.ZoneId
import com.netflix.spinnaker.keel.notifications.NotificationType as Type

class NotificationEventListenerTests : JUnit5Minutests {

  class Fixture {
    val repository: KeelRepository = mockk()
    val releaseArtifact = DummyArtifact(reference = "release")
    val version0 = "fnord-1.0.0-h0.a0a0a0a"
    val version1 = "fnord-1.0.1-h1.b1b1b1b"
    val versions = listOf(version0, version1)

    val resourceTest = resource(
      spec = DummyArtifactReferenceResourceSpec(
        artifactReference = releaseArtifact.reference
      )
    )
    val resourceStaging = resource(
      spec = DummyArtifactReferenceResourceSpec(
        artifactReference = releaseArtifact.reference
      )
    )

    val clock: MutableClock = MutableClock(
      Instant.parse("2020-03-25T00:00:00.00Z"),
      ZoneId.of("UTC")
    )

    val pin = EnvironmentArtifactPin("test", releaseArtifact.reference, version0, "keel@keel.io", "comment")
    val application1 = "fnord"
    val singleArtifactEnvironments = listOf(
      Environment(
        name = "test",
        notifications = setOf(
          NotificationConfig(
            type = NotificationType.slack,
            address = "test",
            frequency = NotificationFrequency.normal
          )
        ),
        resources = setOf(resourceTest)
      ),
      Environment(
        name = "staging",
        notifications = setOf(
          NotificationConfig(
            type = NotificationType.slack,
            address = "staging",
            frequency = NotificationFrequency.quiet
          )
        ),
        resources = setOf(resourceStaging)
      ),
      Environment(
        name = "production",
        notifications = setOf(
          NotificationConfig(
            type = NotificationType.slack,
            address = "prod",
            frequency = NotificationFrequency.verbose
          ),
          NotificationConfig(
            type = NotificationType.slack,
            address = "prod#2",
            frequency = NotificationFrequency.quiet
          ),
          NotificationConfig(
            type = NotificationType.email,
            address = "@prod",
            frequency = NotificationFrequency.verbose
          )
        )
      ),
    )

    val singleArtifactDeliveryConfig = DeliveryConfig(
      name = "manifest_$application1",
      application = application1,
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(releaseArtifact),
      environments = singleArtifactEnvironments.toSet(),
    )

    val slackService: SlackService = mockk()
    val gitDataGenerator: GitDataGenerator = mockk()
    val pinnedNotificationHandler: PinnedNotificationHandler = mockk(relaxUnitFun = true) {
      every {
        supportedTypes
      } returns listOf(Type.ARTIFACT_PINNED)
    }

    val unpinnedNotificationHandler: UnpinnedNotificationHandler = mockk(relaxUnitFun = true) {
      every {
        supportedTypes
      } returns listOf(Type.ARTIFACT_UNPINNED)
    }

    val pausedNotificationHandler: PausedNotificationHandler = mockk(relaxUnitFun = true) {
      every {
        supportedTypes
      } returns listOf(Type.APPLICATION_PAUSED)
    }

    val lifecycleEventNotificationHandler: LifecycleEventNotificationHandler = mockk(relaxUnitFun = true) {
      every {
        supportedTypes
      } returns listOf(Type.LIFECYCLE_EVENT)
    }

    val artifactDeployedNotificationHandler: ArtifactDeploymentNotificationHandler = mockk(relaxUnitFun = true) {
      every {
        supportedTypes
      } returns listOf(Type.ARTIFACT_DEPLOYMENT_SUCCEEDED, Type.ARTIFACT_DEPLOYMENT_FAILED)
    }

    val verificationCompletedNotificationHandler: VerificationCompletedNotificationHandler = mockk(relaxUnitFun = true) {
      every {
        supportedTypes
      } returns listOf(Type.TEST_FAILED, Type.TEST_PASSED)
    }

    val lifecycleEvent = LifecycleEvent(
      type = LifecycleEventType.BAKE,
      scope = LifecycleEventScope.PRE_DEPLOYMENT,
      status = LifecycleEventStatus.FAILED,
      deliveryConfigName = releaseArtifact.deliveryConfigName!!,
      artifactReference = releaseArtifact.reference,
      artifactVersion = version0,
      id = "bake-$version0",
    )

    val artifactVersionVetoedNotification = ArtifactVersionVetoed(
      application1,
      EnvironmentArtifactVeto(
        "production",
        releaseArtifact.reference,
        version0,
        "Spinnaker",
        "Automatically marked as bad because multiple deployments of this version failed."
      ),
      singleArtifactDeliveryConfig)

    var artifactDeploymentFailedNotification = ResourceTaskFailed(
      resource = resourceTest,
      tasks = listOf(
        Task(
          id = "01F22T9NS1X411AG40MF5FJ188",
          name = "Deploy $version0 to server group waffletime-test in test/us-east-1"
        )
      ),
      reason = "Failed to update resource to match definition - Stage createServerGroup timed out after 30 minutes 5 seconds"
    )

    var artifactDeployedNotification = ArtifactDeployedNotification(
      singleArtifactDeliveryConfig,
      version1,
      releaseArtifact,
      singleArtifactDeliveryConfig.environments.first()
    )

    val verificationCompletedNotification = VerificationCompleted(
      application = application1,
      deliveryConfigName = singleArtifactDeliveryConfig.name,
      environmentName = "staging",
      artifactReference = "release",
      artifactType = DEBIAN,
      artifactVersion = "waffle-buttermilk-2.0",
      verificationType = "taste-test",
      verificationId = "my/docker:tag",
      status = ConstraintStatus.FAIL,
      metadata = mapOf(
        "taste" to "excellent",
        "task" to "eater=emily"
      )
    )

    val pinnedNotification = PinnedNotification(singleArtifactDeliveryConfig, pin)
    val pausedNotification = ApplicationActuationPaused(application1, clock.instant(), "user1")
    val scmNotifier: ScmNotifier = mockk()
    private val baseUrlConfig = BaseUrlConfig()
    val networkEndpointProvider: NetworkEndpointProvider = mockk()

    val subject = NotificationEventListener(
      repository = repository,
      clock = clock,
      slackHandlers = listOf(
        pinnedNotificationHandler,
        pausedNotificationHandler,
        unpinnedNotificationHandler,
        lifecycleEventNotificationHandler,
        artifactDeployedNotificationHandler,
        verificationCompletedNotificationHandler
      ),
      scmNotifier = scmNotifier,
      baseUrlConfig = baseUrlConfig,
      networkEndpointProvider = networkEndpointProvider
    )


    fun Collection<String>.toArtifactVersions(artifact: DeliveryArtifact) =
      map { PublishedArtifact(artifact.name, artifact.type, it) }
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("sending notifications with a single environment handler") {
      before {
        every {
          slackService.getUsernameByEmail(any())
        } returns "@keel"

        every {
          slackService.sendSlackNotification("test", any(), any(), any(), any())
        } returns null

        every {
          gitDataGenerator.generateScmInfo(any(), any(), any(), any())
        } returns SectionBlockBuilder()


        every { repository.getArtifactVersion(releaseArtifact, any(), any()) } returns versions.toArtifactVersions(releaseArtifact).first()

        every {
          repository.getArtifactVersionByPromotionStatus(any(), any(), any(), any())
        } returns versions.toArtifactVersions(releaseArtifact).first()

        every {
          repository.getCurrentlyDeployedArtifactVersion(any(), any(), any())
        } returns versions.toArtifactVersions(releaseArtifact).first()

        every {
          repository.latestVersionApprovedIn(any(), any(), any())
        } returns versions.last()

      }

      test("the right (pinned) slack notification was sent out just once") {
        subject.onPinnedNotification(pinnedNotification)
        verify(exactly = 1) {
          pinnedNotificationHandler.sendMessage(any(), any(), any())
        }
        verify(exactly = 0) {
          unpinnedNotificationHandler.sendMessage(any(), any(), any())
        }
      }

      test("only slack notifications are sent out") {
        subject.onPinnedNotification(pinnedNotification.copy(pin = pin.copy(targetEnvironment = "production")))
        verify(exactly = 2) {
          pinnedNotificationHandler.sendMessage(any(), any(), any())
        }
      }

      test("don't send a notification if an environment was not found") {
        subject.onPinnedNotification(pinnedNotification.copy(pin = pin.copy(targetEnvironment = "test#2")))
        verify(exactly = 0) {
          pinnedNotificationHandler.sendMessage(any(), any(), any())
        }
      }
    }

    context("sending notifications to multiple environments") {
      before {
        every {
          repository.getDeliveryConfigForApplication(application1)
        } returns singleArtifactDeliveryConfig
      }
      test("sending pause notifications") {
        subject.onApplicationActuationPaused(pausedNotification)
        verify(exactly = 4) {
          pausedNotificationHandler.sendMessage(any(), any(), any())
        }
      }
    }

    context("lifecycle notifications") {
      before {
        every {
          repository.getDeliveryConfig(any())
        } returns singleArtifactDeliveryConfig

        every { repository.getArtifactVersion(releaseArtifact, any(), any()) } returns versions.toArtifactVersions(releaseArtifact).first()
      }

      test("send notifications to relevant environments only") {
        subject.onLifecycleEvent(lifecycleEvent)
        verify(exactly = 2) {
          lifecycleEventNotificationHandler.sendMessage(any(), any(), any())
        }
      }
    }

    context("artifact deployment notifications") {
      before {
        every { repository.getArtifactVersion(releaseArtifact, any(), any()) } returns versions.toArtifactVersions(releaseArtifact).first()

        every {
          repository.getArtifactVersionByPromotionStatus(any(), any(), any(), any())
        } returns versions.toArtifactVersions(releaseArtifact).first()

        every {
          repository.getCurrentlyDeployedArtifactVersion(any(), any(), any())
        } returns versions.toArtifactVersions(releaseArtifact).first()

        every {
          repository.getDeliveryConfigForApplication(application1)
        } returns singleArtifactDeliveryConfig

        every { repository.getResource(resourceTest.id) } returns resourceTest

        every { scmNotifier.commentOnPullRequest(any(), any(), any()) } just runs

        every { scmNotifier.postDeploymentStatusToCommit(any(), any(), any(), any()) } just runs
      }

      test("send successful deployment notifications using the right handler to the right env") {
        subject.onArtifactVersionDeployed(artifactDeployedNotification)
        verify(exactly = 1) {
          artifactDeployedNotificationHandler.sendMessage(any(), any(), any())
        }
      }

      test("send vetoed deployment notifications using the right handler to the right env") {
        subject.onArtifactVersionVetoed(artifactVersionVetoedNotification)
        verify(exactly = 2) {
          artifactDeployedNotificationHandler.sendMessage(any(), any(), any())
        }
      }

      test("for quiet notification frequency, don't send artifact deployed successfully notification") {
        subject.onArtifactVersionDeployed(artifactDeployedNotification.copy(
          targetEnvironment = singleArtifactEnvironments.find { it.name == "staging" }!!
        ))
        verify(exactly = 0) {
          artifactDeployedNotificationHandler.sendMessage(any(), any(), any())
        }
      }

      test("send failed deployment notifications using right handler to right env") {
        subject.onArtifactVersionDeployFailed(artifactDeploymentFailedNotification)
        verify(exactly = 1) {
          artifactDeployedNotificationHandler.sendMessage(any(), any(), any())
        }
      }

      context("for a preview environment") {
        before {
          every {
            repository.getDeliveryConfigForApplication(application1)
          } returns singleArtifactDeliveryConfig.withPreviewEnvironment()

          every {
            networkEndpointProvider.getNetworkEndpoints(any())
          } returns setOf(NetworkEndpoint(DNS, "us-east-1", "fake.acme.net"))
        }

        test("posts a comment to the associated PR on artifact deployed") {
          val configUnderTest = singleArtifactDeliveryConfig.withPreviewEnvironment()
          with(artifactDeployedNotification.withPreviewEnvironment()) {
            subject.onArtifactVersionDeployed(this)
            verify(exactly = 1) {
              scmNotifier.commentOnPullRequest(configUnderTest, targetEnvironment, any())
            }
          }
        }

        test("PR comment includes endpoint information for applicable resources") {
          val configUnderTest = singleArtifactDeliveryConfig.withPreviewEnvironment()
          with(artifactDeployedNotification.withPreviewEnvironment()) {
            subject.onArtifactVersionDeployed(this)
            val comment = slot<String>()
            verify(exactly = 1) {
              scmNotifier.commentOnPullRequest(configUnderTest, targetEnvironment, capture(comment))
            }
            expectThat(comment.captured).contains("fake.acme.net")
          }
        }

        test("posts successful deployment status to SCM on artifact deployed") {
          val configUnderTest = singleArtifactDeliveryConfig.withPreviewEnvironment()
          with(artifactDeployedNotification.withPreviewEnvironment()) {
            subject.onArtifactVersionDeployed(this)
            verify(exactly = 1) {
              scmNotifier.postDeploymentStatusToCommit(configUnderTest, targetEnvironment, any(), SUCCEEDED)
            }
          }
        }

        test("posts a comment to the associated PR on artifact deployment failed") {
          val configUnderTest = singleArtifactDeliveryConfig.withPreviewEnvironment()
          val previewEnv = configUnderTest.environments.first { it.name == "test" }
          subject.onArtifactVersionDeployFailed(artifactDeploymentFailedNotification)
          verify(exactly = 1) {
            scmNotifier.commentOnPullRequest(configUnderTest, previewEnv, any())
          }
        }

        test("posts failed deployment status to SCM on artifact deployment failure") {
          val configUnderTest = singleArtifactDeliveryConfig.withPreviewEnvironment()
          val previewEnv = configUnderTest.environments.first { it.name == "test" }
          subject.onArtifactVersionDeployFailed(artifactDeploymentFailedNotification)
          verify(exactly = 1) {
            scmNotifier.postDeploymentStatusToCommit(configUnderTest, previewEnv, any(), FAILED)
          }
        }
      }
    }

    context("verification completed notifications") {
      before {
        every { repository.getArtifactVersion(releaseArtifact, any(), any()) } returns versions.toArtifactVersions(releaseArtifact).first()

        every {
          repository.getDeliveryConfig(any())
        } returns singleArtifactDeliveryConfig
      }

      test("verification failed, send notification with right handler") {
        subject.onVerificationCompletedNotification(verificationCompletedNotification)
        verify(exactly = 1) {
          verificationCompletedNotificationHandler.sendMessage(any(), any(), any())
        }
      }

      test("verification passed but frequency is quiet, don't send notification") {
        subject.onVerificationCompletedNotification(verificationCompletedNotification.copy(
          status = ConstraintStatus.PASS
        ))
        verify(exactly = 0) {
          verificationCompletedNotificationHandler.sendMessage(any(), any(), any())
        }
      }

      test("verification passed and frequency is normal, send notification") {
        subject.onVerificationCompletedNotification(verificationCompletedNotification.copy(
          status = ConstraintStatus.PASS,
          environmentName = "test"
        ))
        verify(exactly = 1) {
          verificationCompletedNotificationHandler.sendMessage(any(), any(), any())
        }
      }

      test("verification sent with status which is not passed/failed, don't send notification") {
        subject.onVerificationCompletedNotification(verificationCompletedNotification.copy(
          status = ConstraintStatus.OVERRIDE_FAIL
        ))
        verify(exactly = 0) {
          verificationCompletedNotificationHandler.sendMessage(any(), any(), any())
        }
      }
    }
  }

  private fun ArtifactDeployedNotification.withPreviewEnvironment() =
    copy(
      targetEnvironment = targetEnvironment.copy(isPreview = true).addMetadata(mapOf("pullRequestId" to "42"))
    )

  private fun DeliveryConfig.withPreviewEnvironment() =
    copy(
      environments = environments.map {
        if (it.name == "test") {
          it.copy(isPreview = true).addMetadata(mapOf("pullRequestId" to "42"))
        } else {
          it
        }
      }.toSet()
    )
}
