package com.netflix.spinnaker.keel.ec2.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupOverride

internal interface SecurityGroupSpecMixin {
  @get:JsonInclude(NON_EMPTY)
  val overrides: Map<String, SecurityGroupOverride>

  @get:JsonIgnore
  val id: String
}
