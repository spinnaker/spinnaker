package com.netflix.spinnaker.keel.ec2.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore

interface TargetTrackingPolicyMixin {
  @get:JsonIgnore
  val name: String?
}
