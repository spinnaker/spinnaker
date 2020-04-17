package com.netflix.spinnaker.keel.core

import com.netflix.spinnaker.kork.exceptions.SystemException

/**
 * Sub-classes of this exception type may be thrown during resource desired state resolution
 * implementations to indicate that some object the resource relies on is not (yet) available. For
 * example, an AMI has not yet been baked.
 *
 * Exceptions of this type are not treated as fatal as they are expected to be transient.
 */
abstract class ResourceCurrentlyUnresolvable(message: String, cause: Throwable? = null) :
  SystemException(message, cause)
