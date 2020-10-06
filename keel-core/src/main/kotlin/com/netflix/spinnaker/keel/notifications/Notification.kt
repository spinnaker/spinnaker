package com.netflix.spinnaker.keel.notifications

data class Notification(
  val subject: String,
  val body: String,
  val color: String = "#cccccc"
)