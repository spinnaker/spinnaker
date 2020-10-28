package com.netflix.spinnaker.keel.ec2.jackson.mixins

import com.fasterxml.jackson.annotation.JacksonInject

internal interface ReferenceRuleMixin {
  @get:JacksonInject("name")
  val name: String
}
