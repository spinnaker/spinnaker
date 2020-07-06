package com.netflix.spinnaker.keel.ec2.jackson.mixins

import com.fasterxml.jackson.annotation.JsonAlias

interface BuildInfoMixin {
  @get:JsonAlias("package_name")
  val packageName: String?
}
