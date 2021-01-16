package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.slack.SlackUnpinnedNotification
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.slack.SlackNotifier
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class UnpinnedNotificationHandler (
  private val slackNotifier: SlackNotifier,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String
) : SlackNotificationHandler<SlackUnpinnedNotification> {

  override val type: NotificationType = NotificationType.UNPINNED_ARTIFACT
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackUnpinnedNotification) {
    TODO("Not yet implemented")
  }
}
