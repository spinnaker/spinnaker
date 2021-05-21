package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.NotificationFrequency

/**
 * Plugins can return this class after a constraint state changed
 * event, and we will construct a plugin notification and
 * send it via slack.
 */
data class PluginNotificationConfig(
  val title: String,
  val message: String,
  val status: PluginNotificationsStatus,
  val notificationLevel: NotificationFrequency,
  val provenance: String, //who is sending this!
  val buttonLink: String? = null,
  val buttonText: String? = null
)
