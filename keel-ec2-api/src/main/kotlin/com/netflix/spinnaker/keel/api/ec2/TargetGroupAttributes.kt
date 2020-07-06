package com.netflix.spinnaker.keel.api.ec2

data class TargetGroupAttributes(
  val stickinessEnabled: Boolean = false,
  val deregistrationDelay: Int = 300,
  val stickinessType: String = "lb_cookie",
  val stickinessDuration: Int = 86400,
  val slowStartDurationSeconds: Int = 0,
  val properties: Map<String, Any?> = emptyMap()
)
