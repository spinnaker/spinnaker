package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.schema.Discriminator

interface Verification {
  @Discriminator
  val type: String

  /**
   * Identifier used to distinguish between different instances.
   *
   * TODO: I'm not 100% happy with this approach but we need some way to distinguish verifications
   * in the database.
   */
  val id: String
}
