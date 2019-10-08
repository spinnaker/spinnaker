package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.ResourceId

abstract class ResourceResolutionException(
  type: String,
  resourceId: ResourceId,
  cause: Throwable
) : RuntimeException("Unable to resolve $type state of $resourceId due to: ${cause.message}", cause)

class CannotResolveCurrentState(
  resourceId: ResourceId,
  cause: Throwable
) : ResourceResolutionException("current", resourceId, cause)

class CannotResolveDesiredState(
  resourceId: ResourceId,
  cause: Throwable
) : ResourceResolutionException("desired", resourceId, cause)
