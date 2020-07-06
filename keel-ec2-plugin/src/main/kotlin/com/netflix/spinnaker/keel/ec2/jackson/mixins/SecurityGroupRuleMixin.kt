package com.netflix.spinnaker.keel.ec2.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore

interface SecurityGroupRuleMixin {
  @get:JsonIgnore
  val isSelfReference: Boolean
}
