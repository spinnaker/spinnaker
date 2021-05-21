package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationConfig
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationsStatus.FAILED
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.slack.SlackPluginNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.netflix.spinnaker.time.MutableClock
import com.slack.api.model.block.LayoutBlock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment
import strikt.api.expect
import strikt.assertions.hasSize

class PluginNotificationHandlerTests {

  val slackService: SlackService = mockk(relaxUnitFun = true) {
    every { sendSlackNotification(any(), any(), any(), any(), any()) } returns null
    every { getUsernameByEmailPrefix(any()) } returns "@emburns"
  }
  val scmInfo: ScmInfo = mockk(relaxUnitFun = true) {
    coEvery { getScmInfo() } returns mapOf(
      "stash" to "www.stash.com"
    )
  }
  val artifactVersionLinks: ArtifactVersionLinks = mockk()
  val gitDataGenerator: GitDataGenerator = GitDataGenerator(scmInfo, BaseUrlConfig(), slackService, artifactVersionLinks)
  val clock = MutableClock()
  val springEnv = mockk<Environment>(relaxUnitFun = true) {
    every {getProperty("keel.plugins.notifications.enabled", Boolean::class.java, true) } returns true
  }

  val notification = PluginNotificationConfig(
    title = "title",
    message = "message",
    status = FAILED,
    buttonText = null,
    buttonLink = null,
    notificationLevel = NotificationFrequency.normal,
    provenance = "plugin",
  )
  val notificationWithButton = notification.copy(buttonText = "click me", buttonLink = "http://a.button.link")

  val artifactVersion = PublishedArtifact(
    name = "myartifact",
    reference = "myartifact",
    type = "docker",
    version = "v123",
    gitMetadata = GitMetadata(
      commit = "abc123",
      author = "emburns",
      project = "spkr",
      branch = "main",
      repo = Repo(name = "keel"),
      commitInfo = Commit(
        sha = "abc123",
        message = "I committed this"
      )
    ),
    buildMetadata = BuildMetadata(
      id = 2,
      number = "2"
    )
  )
  val slackNotification = SlackPluginNotification(
    time = clock.instant(),
    application = "myapp",
    config = notification,
    artifactVersion = artifactVersion,
    targetEnvironment = "myenv"
  )

  val subject = PluginNotificationHandler(slackService, gitDataGenerator, springEnv)

  @Test
  fun `generates a notification without button`() {
    val blocks = slot<List<LayoutBlock>>()

    subject.sendMessage(slackNotification, "#channel")
    verify {
      slackService.sendSlackNotification("#channel", capture(blocks), "myapp", any(), any())
    }

    expect {
      that(blocks.captured).hasSize(3)
    }
  }

  @Test
  fun `generates a notification with button`() {
    val blocks = slot<List<LayoutBlock>>()

    subject.sendMessage(slackNotification.copy(config = notificationWithButton), "#channel")
    verify {
      slackService.sendSlackNotification("#channel", capture(blocks), "myapp", any(), any())
    }

    expect {
      that(blocks.captured).hasSize(4)
    }
  }

  @Test
  fun `notifications not sent if disabled`() {
    every { springEnv.getProperty("keel.plugins.notifications.enabled", Boolean::class.java, true) } returns false

    subject.sendMessage(slackNotification.copy(config = notificationWithButton), "#channel")
    verify (exactly = 0) {
      slackService.sendSlackNotification("#channel", any(), "myapp", any(), any())
    }

  }
}
