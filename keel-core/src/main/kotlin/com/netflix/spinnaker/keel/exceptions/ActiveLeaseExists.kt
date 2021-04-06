package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.keel.api.Environment
import java.time.Instant

class ActiveLeaseExists(
  environment: Environment,
  holder: String,
  leasedAt: Instant
) : EnvironmentCurrentlyBeingActedOn("Active lease exists on ${environment.name}: leased by $holder at $leasedAt") {}

