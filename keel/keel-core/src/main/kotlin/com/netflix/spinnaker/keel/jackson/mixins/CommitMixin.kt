package com.netflix.spinnaker.keel.jackson.mixins

import tools.jackson.databind.annotation.JsonSerialize
import com.netflix.spinnaker.keel.jackson.CommitMessageSerializer

internal interface CommitMixin {
  @get:JsonSerialize(using = CommitMessageSerializer::class)
  val message: String?
}