package com.netflix.spinnaker.keel.ec2.jackson.mixins

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY

interface ServerGroupSpecMixin {
  @get:JsonInclude(NON_EMPTY)
  val tags: Map<String, String>?
}
