package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.kork.exceptions.SystemException

abstract class ResourceResolutionException(
  type: String,
  resourceId: String,
  cause: Throwable
) : SystemException("Unable to resolve $type state of $resourceId due to: ${cause.message}", cause)

class CannotResolveCurrentState(
  resourceId: String,
  cause: Throwable
) : ResourceResolutionException("current", resourceId, cause)

class CannotResolveDesiredState(
  resourceId: String,
  cause: Throwable
) : ResourceResolutionException("desired", resourceId, cause)
