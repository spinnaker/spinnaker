package com.netflix.spinnaker.keel.plugin

abstract class ResourceResolutionException(
  type: String,
  resourceId: String,
  cause: Throwable
) : RuntimeException("Unable to resolve $type state of $resourceId due to: ${cause.message}", cause)

class CannotResolveCurrentState(
  resourceId: String,
  cause: Throwable
) : ResourceResolutionException("current", resourceId, cause)

class CannotResolveDesiredState(
  resourceId: String,
  cause: Throwable
) : ResourceResolutionException("desired", resourceId, cause)
