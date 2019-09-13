package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.ResourceId
import java.lang.RuntimeException

class CannotResolveCurrentState(
  resourceId: ResourceId,
  cause: Throwable
) : RuntimeException("Unable to resolve current state of $resourceId", cause)
