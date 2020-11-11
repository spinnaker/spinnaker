package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.schema.Discriminator

interface Verification {
  @Discriminator
  val type: String
}
