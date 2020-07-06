package com.netflix.spinnaker.keel.ec2.jackson.mixins

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonAnyGetter

interface TargetGroupAttributesMixin {
  @get:JsonAlias("stickiness.enabled")
  val stickinessEnabled: Boolean

  @get:JsonAlias("deregistration_delay.timeout_seconds")
  val deregistrationDelay: Int

  @get:JsonAlias("stickiness.type")
  val stickinessType: String

  @get:JsonAlias("stickiness.lb_cookie.duration_seconds")
  val stickinessDuration: Int

  @get:JsonAlias("slow_start.duration_seconds")
  val slowStartDurationSeconds: Int

  @get:JsonAnyGetter
  val properties: Map<String, Any?>
}
